#!/usr/bin/env scala-cli

//> using scala 3.3.4
//> using options -Ysemanticdb
//> using options -sourceroot:.
//> using dep com.lihaoyi::os-lib:0.11.8

import os._

object WorktreeFinish:
  def main(args: Array[String]): Unit =
    val config = parseArgs(args.toList)
    if config.help then
      printUsage()
      sys.exit(0)

    val currentDir = os.pwd
    val repoRoot =
      try {
        os.Path(
          os.proc("git", "rev-parse", "--show-toplevel").call().out.text().trim
        )
      } catch {
        case _: Exception =>
          println("Error: Not inside a git repository")
          sys.exit(1)
      }

    val target = detectTarget(currentDir, repoRoot, config.branch)

    if !os.exists(target.wtDir) then
      println(s"Error: Worktree directory not found at ${target.wtDir}")
      sys.exit(1)

    val isClean = os
      .proc("git", "status", "--porcelain")
      .call(cwd = target.wtDir)
      .out
      .text()
      .trim
      .isEmpty
    if !isClean then
      config.message match
        case Some(message) =>
          println("Committing changes in worktree...")
          os.proc("git", "add", ".").call(cwd = target.wtDir)
          os.proc("git", "commit", "-m", message).call(cwd = target.wtDir)
        case None =>
          println(
            "Error: Worktree has uncommitted changes. Commit them first, or pass --message."
          )
          sys.exit(1)

    val finalBase = defaultBranch(target.mainRepo)

    if config.mergeLocal then
      println(s"Merging branch '${target.branch}' into '$finalBase'...")
      os.proc("git", "checkout", finalBase).call(cwd = target.mainRepo)
      os.proc(
        "git",
        "merge",
        target.branch,
        "--no-ff",
        "-m",
        s"Merge branch '${target.branch}' into $finalBase"
      ).call(cwd = target.mainRepo)
    else println("Skipping local merge; assuming PR-based merge flow.")

    println(s"Removing worktree at '${target.wtDir}'...")
    try {
      os.proc("git", "worktree", "remove", "--force", target.wtDir.toString)
        .call(cwd = target.mainRepo)
    } catch {
      case _: Exception => // ignore if already gone
    }

    println(s"Deleting local branch '${target.branch}'...")
    os.proc("git", "branch", "-d", target.branch)
      .call(cwd = target.mainRepo, check = false)
      .exitCode == 0 ||
      os.proc("git", "branch", "-D", target.branch)
        .call(cwd = target.mainRepo, check = false)
        .exitCode == 0

    if config.deleteRemote then
      println(s"Deleting remote branch 'origin/${target.branch}'...")
      os.proc("git", "push", "origin", "--delete", target.branch)
        .call(cwd = target.mainRepo, check = false)

    println(
      "========================================================================"
    )
    println("Worktree cleaned up successfully!")
    println(s"  Branch: ${target.branch}")
    println(s"  Local merge: ${if config.mergeLocal then s"merged into $finalBase" else "skipped"}")
    println(s"  Removed worktree: ${target.wtDir}")
    println(s"  Returned to: ${target.mainRepo}")
    println("")
    println("To return your shell to the main repository, run:")
    println(s"  cd ${target.mainRepo}")
    println(
      "========================================================================"
    )

  private final case class Config(
      branch: Option[String],
      mergeLocal: Boolean,
      deleteRemote: Boolean,
      message: Option[String],
      help: Boolean
  )

  private final case class Target(branch: String, wtDir: os.Path, mainRepo: os.Path)

  private def parseArgs(args: List[String]): Config =
    def loop(rest: List[String], config: Config): Config =
      rest match
        case Nil => config
        case ("--help" | "-h") :: tail =>
          loop(tail, config.copy(help = true))
        case "--merge-local" :: tail =>
          loop(tail, config.copy(mergeLocal = true))
        case "--delete-remote" :: tail =>
          loop(tail, config.copy(deleteRemote = true))
        case ("--message" | "-m") :: message :: tail =>
          loop(tail, config.copy(message = Some(message)))
        case ("--message" | "-m") :: Nil =>
          println("Error: --message requires a value")
          sys.exit(1)
        case branch :: tail if branch.startsWith("-") =>
          println(s"Error: Unknown option: $branch")
          sys.exit(1)
        case branch :: tail =>
          loop(tail, config.copy(branch = Some(branch)))

    loop(
      args,
      Config(branch = None, mergeLocal = false, deleteRemote = false, message = None, help = false)
    )

  private def detectTarget(currentDir: os.Path, repoRoot: os.Path, branchArg: Option[String]): Target =
    val wtPattern = "\\\\.worktrees/([^/]+)".r
    val currentDirStr = currentDir.toString
    wtPattern.findFirstMatchIn(currentDirStr) match
      case Some(m) =>
        val idx = currentDirStr.indexOf("/.worktrees/")
        Target(
          branch = branchArg.getOrElse(m.group(1)),
          wtDir = repoRoot,
          mainRepo = os.Path(currentDirStr.substring(0, idx))
        )
      case None =>
        branchArg match
          case Some(branch) =>
            Target(branch = branch, wtDir = repoRoot / ".worktrees" / branch, mainRepo = repoRoot)
          case None =>
            println("Error: Not in a worktree. Please provide the branch name as an argument.")
            sys.exit(1)

  private def defaultBranch(mainRepo: os.Path): String =
    val base =
      try {
        val raw = os
          .proc(
            "git",
            "symbolic-ref",
            "--quiet",
            "--short",
            "refs/remotes/origin/HEAD"
          )
          .call(cwd = mainRepo)
          .out
          .text()
          .trim
        raw.stripPrefix("origin/")
      } catch {
        case _: Exception => "main"
      }

    if os
        .proc("git", "show-ref", "--verify", "--quiet", s"refs/heads/$base")
        .call(cwd = mainRepo, check = false)
        .exitCode == 0
    then base
    else if os
        .proc("git", "show-ref", "--verify", "--quiet", "refs/heads/master")
        .call(cwd = mainRepo, check = false)
        .exitCode == 0
    then "master"
    else "main"

  private def printUsage(): Unit =
    println("Usage: scala-cli run scripts/worktree-finish.scala -- [options] [branch]")
    println("")
    println("Options:")
    println("  --merge-local       Merge the task branch into the default branch before cleanup.")
    println("  --delete-remote     Delete origin/<branch> after local cleanup.")
    println("  -m, --message MSG   Commit dirty worktree changes with MSG before cleanup.")
    println("  -h, --help          Show this help.")
