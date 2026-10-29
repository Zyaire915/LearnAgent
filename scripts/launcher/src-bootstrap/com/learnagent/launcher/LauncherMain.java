package com.learnagent.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JOptionPane;

/**
 * LearnAgent 引导启动器（feature/windows-launcher）。
 *
 * <p>由 jpackage 打包为可双击的 {@code LearnAgent.exe}（发布包根目录），职责刻意保持极小：
 *
 * <ol>
 *   <li>定位应用目录（jpackage 会注入 {@code APPDIR} 环境变量）；</li>
 *   <li>按验收要求计算数据目录 {@code %LOCALAPPDATA%\LearnAgent}；</li>
 *   <li>调用同目录 {@code launcher\Start-LearnAgent.ps1}，由 PowerShell 完成依赖检查、
 *       内置便携 PostgreSQL 初始化/启动、后端（JAR + 已构建 Vue 前端）启动与打开浏览器。</li>
 * </ol>
 *
 * <p>安全说明：本引导不包含、也不读取任何 API Key；密钥只保存在数据目录的
 * {@code learnagent.env} 或源码仓库 {@code .env} 中，由 PowerShell 在运行时读取，
 * 因此不会编译进 EXE，也不会进入仓库或发布包。
 *
 * <p>重复启动的防重入在 PowerShell 层完成（命名互斥量 + {@code /api/system/status} 健康探测），
 * 已运行时只会再打开一个浏览器页面，不会产生第二份后端或数据库进程。
 *
 * <p>编译与打包见 {@code scripts\build-release.ps1}（javac + jar，JDK 21 自带，无需额外工具链）。
 */
public final class LauncherMain {

  private LauncherMain() {
  }

  public static void main(String[] args) {
    Path appDir = resolveAppDir();
    Path startScript = appDir.resolve("launcher").resolve("Start-LearnAgent.ps1");
    if (!Files.isRegularFile(startScript)) {
      showError("未找到启动脚本：\n" + startScript
          + "\n\n发布包可能不完整，请重新解压完整的 LearnAgent 发布包（LearnAgent-*-windows-x64.zip）后再试。");
      return;
    }

    Path dataRoot = resolveDataRoot(appDir);

    ProcessBuilder builder = new ProcessBuilder(
        "powershell.exe",
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", startScript.toString(),
        "-ProjectRoot", appDir.toString(),
        "-DataRoot", dataRoot.toString(),
        "-PopupOnError");
    builder.environment().putIfAbsent("LEARNAGENT_LAUNCHER_EXE", "jpackage");

    try {
      builder.start();
    } catch (IOException ex) {
      showError("无法启动 LearnAgent 启动脚本：\n" + startScript
          + "\n\n错误信息：" + ex.getMessage()
          + "\n\n请确认系统自带的 Windows PowerShell 未被组策略禁用。");
    }
  }

  /**
   * jpackage 生成的 Windows 启动器会把应用镜像根目录放入环境变量 {@code APPDIR}；
   * 直接以 {@code java -jar} 方式运行（开发调试）时回退到当前工作目录。
   */
  private static Path resolveAppDir() {
    String appDir = System.getenv("APPDIR");
    if (appDir == null || appDir.isBlank()) {
      appDir = System.getProperty("user.dir");
    }
    return Paths.get(appDir == null ? "." : appDir).toAbsolutePath().normalize();
  }

  /**
   * 验收要求（docs/TEAM_BRANCHES.md #6）：所有数据写入 LOCALAPPDATA。
   * 极少数未定义 LOCALAPPDATA 的环境回退到应用目录下的 data 子目录。
   */
  private static Path resolveDataRoot(Path appDir) {
    String localAppData = System.getenv("LOCALAPPDATA");
    if (localAppData == null || localAppData.isBlank()) {
      return appDir.resolve("data");
    }
    return Paths.get(localAppData).resolve("LearnAgent");
  }

  /** 无控制台时的错误展示：优先图形对话框，失败则写标准错误。 */
  private static void showError(String message) {
    try {
      JOptionPane.showMessageDialog(null, message, "LearnAgent 启动失败", JOptionPane.ERROR_MESSAGE);
    } catch (Throwable ignored) {
      System.err.println(message);
    }
  }
}
