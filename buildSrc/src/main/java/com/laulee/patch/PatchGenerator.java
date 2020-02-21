package com.laulee.patch;

import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Created by laulee on 2020-02-21.
 */
public class PatchGenerator {

    File patchFile;
    Map<String, String> oldHexs;
    private JarOutputStream jarOutputStream;
    private File jarFile;

    public PatchGenerator(File patchFile, File jarFile, File oldFile) {
        this.patchFile = patchFile;
        this.jarFile = jarFile;
        if (oldFile.exists()) {
            oldHexs = Util.readHex(oldFile);
        }
    }

    /**
     * @param className
     * @param hex
     * @param byteCode
     */
    public void checkClass(String className, String hex, byte[] byteCode) {
        if (Util.isEmpty(oldHexs)) {
            return;
        }

        String oldHex = oldHexs.get(className);
        if (oldHex == null || !oldHex.equals(hex)) {
            JarOutputStream jarOutputStream = getOutput();

            try {
                jarOutputStream.putNextEntry(new JarEntry(className));
                jarOutputStream.write(byteCode);
                jarOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private JarOutputStream getOutput() {
        if (jarOutputStream == null) {
            try {
                jarOutputStream = new JarOutputStream(new FileOutputStream(jarFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return jarOutputStream;
    }
}
