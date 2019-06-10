package com.zk.android.emotioncamera;

import java.util.Comparator;

public class MyComparator implements Comparator {

    /**
     * 排序方式
     * @param arg0
     * @param arg1
     * @return
     */
    @Override
    public int compare(Object arg0, Object arg1) {
        String  name1= (String)arg0;
        String name2 = (String)arg1;
        String numStr1 = name1.substring(47, 52);
        String numStr2 = name2.substring(47, 52);
        int num1 = Integer.parseInt(numStr1);
        int num2 = Integer.parseInt(numStr2);
        return num1 - num2;
    }
}