package com.typesafe.sbtrc
package server

import ipc.{MultiClientServer=>IpcServer, JsonWriter}
import com.typesafe.sbtrc.protocol.{Envelope, Request}
import sbt.server.ServerRequest



/** This class represents an external client into the sbt server.
 *
 * We forward messages from the client into the sbt build loop.
 */
class SbtClientHandler (
    val id: String, 
    ipc: IpcServer,
    msgHandler: ServerRequest => Unit,
    closed: () => Unit) extends sbt.server.AbstractSbtClient {
  
  // TODO - Configure this location.
  // TODO - Is this thread safe-ish?
  private val log = new FileLogger(new java.io.File(s".sbtserver/connections/${id}.log"))
  
  private val running = new java.util.concurrent.atomic.AtomicBoolean(true)
  def isAlive: Boolean = clientThread.isAlive && running.get
  private object clientThread extends Thread(s"sbt-client-handler-$id") {
    final override def run(): Unit = {
      while(running.get) {
        try readNextMessage()
        catch {
          case e: Throwable =>
            // On any throwable, we'll shut down this connection as bad.
            log.error(s"Client $id had error, shutting down", e)
            e.printStackTrace(System.err)
            running.set(false)
        }
      }
      log.log(s"Stopping client.")
      // Send the stopped message to this client
      try send(protocol.Stopped)
      catch {
        case e: Exception =>
          // We ignore any exception trying to stop things.
          log.log(s"Error trying to stop this client: ${e.getMessage}")
      }
      // It's ok to close this connection when we're done.
      ipc.close()
      // Here we send a client disconnected message to the main sbt
      // engine so it stops using this client.
      msgHandler(ServerRequest(SbtClientHandler.this, protocol.ClientClosedRequest()))
      // Here we tell the server thread handler...
      closed()
    }
    private def readNextMessage(): Unit = {
      log.log("Reading next message from client.")
      Envelope(ipc.receive()) match {
          case Envelope(_, _, msg: Request) =>
            log.log(s"Got request: $msg")
            val request = ServerRequest(SbtClientHandler.this, msg)
            msgHandler(request)
          case Envelope(_,_,msg) =>
            sys.error("Unable to handle client request: " + msg)
        }
    }
  }
  // Automatically start listening for client events.
  clientThread.start()
  
  // ipc is synchronized, so this is ok.
  def send[T: JsonWriter](msg: T): Unit = {
    // For now we start ignoring the routing...
    log.log(s"Sending msg to client $id: $msg")
    if(isAlive) ipc.replyJson(0L, msg)
  }

  def shutdown(): Unit = {
    running.set(false)
  }
  def join(): Unit = clientThread.join()
  
  
  override def equals(o: Any): Boolean =
    o match {
      case x: SbtClientHandler => id == x.id
      case _ => false
    }
  override def hashCode = id.hashCode
  override def toString = "LiveClient("+id+")"
}