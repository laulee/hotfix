package com.laulee.hotfix;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HotFix {

    private static String TAG = "HotFix";

    /**
     * 安装补丁包
     *
     * @param myApplication
     * @param patchFile
     */
    public static void installPatch(Application myApplication, File patchFile) {
        if (patchFile.exists()) {
            Log.d(TAG, "installPatch");
            try {
                ClassLoader classLoader = myApplication.getClassLoader();
                //Android N 混合编译导致一些频繁使用的热代码class被添加到app image 中，导致热修复不成功，
                // 采用微信Tinker自定义PathClassLoader替换系统的，这样就能保证加载我们新增的class
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N) {
                    try {
                        classLoader = NewClassLoaderInjector.inject(myApplication, classLoader);
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
                //1、将补丁包转换成element[]
                //1.1通过反射获取classLoader中的pathlist属性
                Field dexPathListField = ShareReflectUtil.findField(classLoader, "pathList");

                ArrayList<File> files = new ArrayList<>();
                files.add(patchFile);
                ArrayList<IOException> exceptions = new ArrayList<>();
                File dirFile = myApplication.getFilesDir();
                Object dexPathList = null;
                dexPathList = dexPathListField.get(classLoader);
                //通过反射获取makeDexElement方法
                Object[] elements = makeDexElements(dexPathList, files, dirFile, exceptions, classLoader);
                //获取原来的elements数组
                Field oldField = ShareReflectUtil.findField(dexPathList, "dexElements");
                Object[] oldElements = (Object[]) oldField.get(dexPathList);
                Object[] mergeElement = copyArray(elements, oldElements);
                //将合并后的element加载进去
                oldField.set(dexPathList, mergeElement);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * A wrapper around
     * {@code private static final dalvik.system.DexPathList#makeDexElements}.
     */
    private static Object[] makeDexElements(
            Object dexPathList, ArrayList<File> files, File optimizedDirectory,
            ArrayList<IOException> suppressedExceptions, ClassLoader classloader)
            throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {

        //7.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Method makeDexElementsMetod =
                    ShareReflectUtil.findMethod(dexPathList, "makeDexElements", List.class, File.class,
                            List.class, ClassLoader.class);
            return (Object[]) makeDexElementsMetod.invoke(dexPathList, files, optimizedDirectory,
                    suppressedExceptions, classloader);
        }
        //6.0+
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Method makeDexElementsMetod =
                    ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class, File.class,
                            List.class);
            return (Object[]) makeDexElementsMetod.invoke(dexPathList, files, optimizedDirectory,
                    suppressedExceptions);
        }
        //4.4.4-5.0+
        else {
            Method makeDexElementsMetod =
                    ShareReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
                            ArrayList.class);

            return (Object[]) makeDexElementsMetod.invoke(dexPathList, files, optimizedDirectory,
                    suppressedExceptions);
        }
    }

    /**
     * @param elements
     * @param oldElements
     * @return
     */
    private static Object[] copyArray(Object[] elements, Object[] oldElements) {
        Object[] merge = (Object[]) Array.newInstance(
                oldElements.getClass().getComponentType(), elements.length + oldElements.length);
        //先拷贝修复的class,这样系统就会优先加载新的class，不会加载bug class达到修复的目的
        System.arraycopy(elements, 0, merge, 0, elements.length);
        //再拷贝原来的element[]
        System.arraycopy(oldElements, 0, merge, elements.length, oldElements.length);
        return merge;
    }
}
