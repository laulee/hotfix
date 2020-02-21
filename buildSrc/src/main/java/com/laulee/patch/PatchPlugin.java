package com.laulee.patch;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.utils.FileUtils;

import org.antlr.v4.misc.Utils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskOutputs;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;

/**
 * Created by laulee on 2020-02-21.
 */
public class PatchPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        if (!project.getPlugins().hasPlugin(AppPlugin.class)) {
            throw new GradleException("无法在非Android applicaiton插件中使用热修复插件");
        }

        //创建一个patch配置
        //这样就能在build.gradle文件中引入插件
        project.getExtensions().create("patch", PatchExtension.class);

        //afterEvalutate表示在解析完成之后再执行我们的代码
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project) {

                PatchExtension patchExtension = project.getExtensions().findByType(PatchExtension.class);
                //获取配置debug模式下是否开启热修复
                boolean debugOn = patchExtension.debugOn;

                //得到Android的配置
                AppExtension appExtension = project.getExtensions().getByType(AppExtension.class);
                //android项目默认会有debug和release
                //那么getApplicationVariants就是包含debug和release集合，all表示对集合进行遍历
                appExtension.getApplicationVariants().all(new Action<ApplicationVariant>() {

                    @Override
                    public void execute(ApplicationVariant applicationVariant) {
                        //当前用户是debug模式，并且是否配置debug模式下开启
                        if (applicationVariant.getName().contains("debug") && !debugOn) {
                            return;
                        }
                        //如果开启了
                        configTasks(project, applicationVariant, patchExtension);
                    }
                });

            }
        });
    }

    /**
     * 配置任务
     *
     * @param project
     * @param applicationVariant
     * @param patchExtension
     */
    private void configTasks(Project project, ApplicationVariant applicationVariant, PatchExtension patchExtension) {


        //获得：debug、release模式
        String variantName = applicationVariant.getName();
        //首字母大写
        String capitalizeName = Utils.capitalize(variantName);
        //创建惹媳妇输出目录
        File outputDir;
        //如果没配置 默认创建在build/patch/debug(release)目录下
        if (Util.isEmpty(patchExtension.output)) {
            outputDir = new File(project.getBuildDir(), "patch/" + variantName);
        } else {
            outputDir = new File(patchExtension.output, variantName);
        }

        outputDir.mkdirs();

        //获得Android混淆任务
        Task proguardTask = project.getTasks().findByName("transformClassesAndResourcesWithProguardFor" + capitalizeName);

        //将mapping文件保存下来
        File mappingBak = new File(outputDir, "mapping.txt");
        //如果没开启混淆，则为null，不需要备份mapping
        if (proguardTask != null) {
            //dolast 在任务之后再执行action
            proguardTask.doLast(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    //混淆任务输出的所有文件
                    TaskOutputs taskOutputs = proguardTask.getOutputs();
                    Set<File> files = taskOutputs.getFiles().getFiles();
                    for (File file : files) {
                        if (file.getName().endsWith("mapping.txt")) {
                            try {
                                FileUtils.copyFile(file, mappingBak);
                                project.getLogger().error("备份的mapping文件:" + mappingBak.getCanonicalPath());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }

        //将上次混淆的mapping应用到本次
        Util.appMapping(proguardTask, mappingBak);

        //在混淆后 记录类的hash值，并生成补丁包
        File hashTxt = new File(outputDir, "hash.txt");

        //需要打包不定的类生成jar包
        File patchClassFile = new File(outputDir, "patchClass.jar");
        //用dx打包生成的jar
        File patchFile = new File(outputDir, "patch.jar");

        //打包dex任务
        Task dexTask = project.getTasks().findByName("transformClassesWithDexBuilderFor" + capitalizeName);

        //doFirst 在任务之前
        //在将class打包dex之前，记录每个class的md5 hash值
        dexTask.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {

                String applicationName = patchExtension.applicationName;
                applicationName = applicationName.replaceAll("//.", Matcher.quoteReplacement(File.separator));

                PatchGenerator patchGenerator = new PatchGenerator(patchClassFile, patchFile, mappingBak);
                //记录类的md5
                Map<String, String> newHexs = new HashMap<>();
                //任务的输入。dex打包任务要输入什么，自然是所有的class与jar包了
                Set<File> files = dexTask.getInputs().getFiles().getFiles();
                for (File file : files) {
                    String filePath = file.getAbsolutePath();
                    if (filePath.endsWith(".jar")) {
                        processJar(applicationName, patchGenerator, file, newHexs);
                    }
                }
            }
        });
    }

    /**
     * 记录jar中的类
     *
     * @param applicationName
     * @param patchGenerator
     * @param file
     * @param newHexs
     */
    private void processJar(String applicationName, PatchGenerator patchGenerator, File file, Map<String, String> newHexs) {

        applicationName = applicationName.replaceAll(Matcher.quoteReplacement(File.separator), "/");

        File bakJar = new File(file.getParent(), file.getName() + ".bak");

        try {
            JarFile jarFile = new JarFile(file);
            Enumeration<JarEntry> enumerations = jarFile.entries();
            while (enumerations.hasMoreElements()) {

                JarEntry jarEntry = enumerations.nextElement();

                String className = jarEntry.getName();
                if (className.endsWith(".class") && !className.startsWith(applicationName)
                        && !Util.isAndroidClass(className) && !className.startsWith("com/laulee/patch")) {
                    byte[] bytes = Util.file2byte(file);
                    String hex = Util.md5(bytes);
                    newHexs.put(className, hex);

                    //对比缓存的md5不一致则放入补丁中
                    patchGenerator.checkClass(className, hex, bytes);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
