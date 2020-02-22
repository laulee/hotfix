package com.laulee.patch;

import com.android.build.gradle.AppExtension;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
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
    private Project project;

    public PatchGenerator(Project project, File jarFile, File patchFile, File oldFile) {
        this.patchFile = patchFile;
        this.jarFile = jarFile;

        if (!this.jarFile.exists()) {
            try {
                this.jarFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.project = project;

        if (oldFile.exists()) {
            project.getLogger().error("oldFile" + oldFile.getAbsolutePath());
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
        project.getLogger().error(className + " oldHex =" + oldHex + " newHex = " + hex);
        if (oldHex == null || !oldHex.equals(hex)) {
            project.getLogger().error(className + " 写入补丁包");
            JarOutputStream jarOutputStream = getOutput();
            try {
                jarOutputStream.putNextEntry(new JarEntry(className));
                jarOutputStream.write(byteCode);
                jarOutputStream.closeEntry();
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

    public void generator() {
        if (!jarFile.exists()) {
            project.getLogger().error("jarFile is null");
            return;
        }

        try {
            JarOutputStream jarOutputStream = getOutput();
            jarOutputStream.close();
            Properties properties = new Properties();
            File file = project.getRootProject().file("local.properties");

            String sdkDir;
            if (file.exists()) {
                properties.load(new FileInputStream(file));
                sdkDir = properties.getProperty("sdk.dir");
            } else {
                sdkDir = System.getenv("ANDROID_HOME");
            }

            AppExtension android = project.getExtensions().getByType(AppExtension.class);
            String buildToolsVersion = android.getBuildToolsVersion();
            //windows使用 dx.bat命令,linux/mac使用 dx命令
            String cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? ".bat" : "";
            // 执行：dx --dex --output=output.jar input.jar
            // windows 执行前面要加 cmd /c
            String dxPath = "cmd /c " + sdkDir + "\\build-tools\\" + buildToolsVersion +
                    "\\dx" + cmdExt;
            String patch = "--output=" + patchFile.getAbsolutePath();
            String cmd = dxPath + " --dex " + patch + " " + jarFile.getAbsolutePath();
            Process process =
                    Runtime.getRuntime().exec(cmd);
            process.waitFor();
//            jarFile.delete();
            //命令执行失败
            if (process.exitValue() != 0) {
                throw new IOException("generate patch error:" + cmd);
            }

            project.getLogger().error("patch generated in : " + patchFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
