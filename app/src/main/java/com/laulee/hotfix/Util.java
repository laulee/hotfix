package com.laulee.hotfix;


public class Util {
    public Util() {
        //依赖别的dex 否则mainactivity会被标记class_ispre
    }

    public static void test(){
        System.out.println("修复bug");
//        throw new RuntimeException("参数错误");
    }
}
