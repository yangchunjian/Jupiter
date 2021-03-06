/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jupiter.rpc.load.balance;

import org.jupiter.common.util.SystemClock;
import org.jupiter.transport.Directory;
import org.jupiter.transport.channel.CopyOnWriteGroupList;
import org.jupiter.transport.channel.JChannelGroup;

/**
 *
 * jupiter
 * org.jupiter.rpc.load.balance
 *
 * @author jiachun.fjc
 */
final class WeightArray {

    private int[] array;

    WeightArray(int length) {
        array = new int[length];
    }

    int get(int index) {
        if (index >= array.length) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return array[index];
    }

    void set(int index, int value) {
        if (index >= array.length) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        array[index] = value;
    }

    int length() {
        return array.length;
    }

    int getSumWeight() {
        return array[array.length - 1];
    }

    boolean isAllSameWeight() {
        return array == null;
    }

    void clear() {
        array = null;
    }

    static WeightArray computeWeightArray(CopyOnWriteGroupList groups, JChannelGroup[] elements, Directory directory) {
        int length = elements.length;

        WeightArray weightArray = new WeightArray(length);

        boolean allWarmUpComplete = elements[0].isWarmUpComplete();
        boolean allSameWeight = true;
        int firstVal = getWeight(elements[0], directory);
        weightArray.set(0, firstVal);
        for (int i = 1; i < length; i++) {
            allWarmUpComplete = (allWarmUpComplete && elements[i].isWarmUpComplete());
            int preVal = weightArray.get(i - 1);
            int curVal = getWeight(elements[i], directory);
            allSameWeight = allSameWeight && firstVal == curVal;

            // [value = preVal + curVal] is for binary search
            weightArray.set(i, preVal + curVal);
        }

        if (allWarmUpComplete) {
            if (allSameWeight) {
                weightArray.clear();
            }
            groups.setWeightArray(elements, directory.directoryString(), weightArray);
        }

        return weightArray;
    }

    // 计算权重, 包含预热逻辑
    static int getWeight(JChannelGroup group, Directory directory) {
        int weight = group.getWeight(directory);
        int warmUp = group.getWarmUp();
        int upTime = (int) (SystemClock.millisClock().now() - group.timestamp());

        if (upTime > 0 && upTime < warmUp) {
            // 对端服务预热中, 计算预热权重
            weight = (int) (((float) upTime / warmUp) * weight);
        }

        return weight > 0 ? weight : 0;
    }

    static int binarySearchIndex(WeightArray weightArray, int length, int value) {
        int low = 0;
        int high = length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = weightArray.get(mid);

            if (midVal < value) {
                low = mid + 1;
            } else if (midVal > value) {
                high = mid - 1;
            } else {
                return mid;
            }
        }

        return low;
    }
}
