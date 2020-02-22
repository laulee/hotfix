package com.laulee.patch;

import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.transforms.ProGuardTransform;

import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.Task;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by laulee on 2020-02-21.
 */
class Util {


    /**
     * 判断是否为空
     *
     * @param output
     * @return
     */
    public static boolean isEmpty(String output) {
        return output == null || output.length() == 0;
    }

    /**
     * 将混淆mapping映射到文件
     *
     * @param proguardTask
     * @param mappingBak
     */
    public static void appMapping(Task proguardTask, File mappingBak) {
        //上次混淆的mapping文件存在并且也开启了混淆任务
        if (mappingBak.exists() && proguardTask != null) {
            //将上次混淆的mapping应用到本次
            TransformTask task = (TransformTask) proguardTask;
            ProGuardTransform proGuardTransform = (ProGuardTransform) task.getTransform();
            proGuardTransform.applyTestedMapping(mappingBak);
        }
    }

    /**
     * 是不是Android包下的类
     *
     * @param className
     * @return
     */
    public static boolean isAndroidClass(String className) {
        return className.startsWith("android");
    }

    public static String md5(byte[] bytes) {
        return DigestUtils.md5Hex(bytes);
    }

    public static byte[] file2byte(File file) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(file, "r");
            byte[] buffer = new byte[(int) r.length()];
            r.readFully(buffer);
            r.close();
            return buffer;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    /**
     * 读取文件中的md5键值对
     *
     * @param oldFile
     * @return
     */
    public static Map<String, String> readHex(File oldFile) {
        Map<String, String> hex = new HashMap<>();

        BufferedReader bufferedReader = null;

        try {
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(oldFile)));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] list = line.split(":");
                if (list != null && list.length == 2) {
                    hex.put(list[0], list[1]);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return hex;
    }

    public static void write(Map<String, String> newHexs, File hashTxt) {

        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(hashTxt);
            for (String key : newHexs.keySet()) {
                String line = key + ":" + newHexs.get(key) + "\n";
                fileOutputStream.write(line.getBytes());
            }
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
