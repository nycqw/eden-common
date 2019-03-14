package com.eden.common.util;

/**
 * @author chenqw
 * @version 1.0
 * @since 2018/11/14
 */
public class ArrayUtil {

    public static <T> int indexOf(T[] arr, String element) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(element)) {
                return i;
            }
        }
        return -1;
    }

    public static <T> int lastIndexOf(T[] arr, String element) {
        for (int i = arr.length - 1; i >= 0; i--) {
            if (arr[i].equals(element)) {
                return i;
            }
        }
        return -1;
    }
}
