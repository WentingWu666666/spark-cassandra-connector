package com.datastax.spark.connector.ccm

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.datastax.oss.driver.api.core.Version
import org.apache.commons.exec.{CommandLine, ExecuteWatchdog, LogOutputStream, _}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try

class CcmBridge(config: CcmConfig) extends AutoCloseable {

  private val executor = config.mode.executor(config)
  private val started = new AtomicBoolean()

  def getDseVersion: Option[Version] = config.getDseVersion

  def getCassandraVersion: Version = config.getCassandraVersion

  def execute(args: String*): Unit = executor.execute(args: _*)

  def executeUnsanitized(args: String*): Unit = executor.executeUnsanitized(args: _*)

  def create(clusterName: String): Unit = executor.create(clusterName)

  def remove(): Unit = executor.remove()

  override def close(): Unit = {
    remove()
  }

  private def start(n: Int): Unit = {
    val formattedJvmArgs = config.jvmArgs.map(arg => s" --jvm_arg=$arg").mkString(" ")
    execute(s"node$n", "start", formattedJvmArgs + "--wait-for-binary-proto")
  }

  def start(): Unit = {
    if (started.compareAndSet(false, true)) {
      config.nodes.foreach(start)
    }
  }

  def stop(): Unit = {
    if (started.compareAndSet(true, false)) {
      execute("stop")
    }
  }

  def pause(n: Int): Unit = {
    execute(s"node$n", "pause")
  }

  def resume(n: Int): Unit = {
    execute(s"node$n", "resume")
  }

  def dsetool(n: Int, args: String*): Unit = {
    execute(s"node$n dsetool ${args.mkString(" ")}")
  }

  def nodetool(n: Int, args: String*): Unit = {
    execute(s"node$n nodetool ${args.mkString(" < ")}")
  }

  def refreshSizeEstimates(n: Int): Unit = {
    nodetool(n, "refreshsizeestimates")
  }

  def flush(n: Int): Unit = {
    execute("node" + n, "flush")
  }

}

object CcmBridge {

  private val logger: Logger = LoggerFactory.getLogger(classOf[CcmBridge])

  def execute(cli: CommandLine): Unit = {
    logger.debug("Executing: " + cli)

    val watchDog: ExecuteWatchdog = new ExecuteWatchdog(TimeUnit.MINUTES.toMillis(10))
    val outStream = new LogOutputStream() {
      override def processLine(line: String, logLevel: Int): Unit = logger.debug("ccmout> {}", line)
    }
    val errStream = new LogOutputStream() {
      override def processLine(line: String, logLevel: Int): Unit = logger.error("ccmerr> {}", line)
    }

    try {
      val executor = new DefaultExecutor()
      val streamHandler = new PumpStreamHandler(outStream, errStream)
      executor.setStreamHandler(streamHandler)
      executor.setWatchdog(watchDog)
      val retValue = executor.execute(cli)
      if (retValue != 0) {
        logger.error(
          "Non-zero exit code ({}) returned from executing ccm command: {}", retValue, cli)
      }
    } catch {
      case _: IOException if watchDog.killedProcess() =>
        throw new RuntimeException("The command '" + cli + "' was killed after 10 minutes")
      case ex: IOException =>
        throw new RuntimeException("The command '" + cli + "' failed to execute", ex)
    } finally {
      Try(outStream.close())
      Try(errStream.close())
    }
  }

}