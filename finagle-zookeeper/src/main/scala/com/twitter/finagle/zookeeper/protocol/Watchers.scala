package com.twitter.finagle.zookeeper.protocol

import com.twitter.finagle.NoStacktrace
import com.twitter.util.{Future, Promise}
import scala.collection.mutable

trait WatchManager {
  def apply(evt: WatcherEvent): Future[Unit] =
    apply(WatchedEvent(evt))

  def apply(evt: WatchedEvent): Future[Unit]

  def existsWatch(path: String): Future[WatchedEvent]
  def dataWatch(path: String): Future[WatchedEvent]
  def childrenWatch(path: String): Future[WatchedEvent]
}

object DefaultWatchManager extends WatchManager {
  private[this] val dataTable = new mutable.HashMap[String, Promise[WatchedEvent]]
  private[this] val existTable = new mutable.HashMap[String, Promise[WatchedEvent]]
  private[this] val childTable = new mutable.HashMap[String, Promise[WatchedEvent]]

  // TODO: should this be shunted off to another threadpool?
  // for now the read thread will end up satisfying all watches
  def apply(evt: WatchedEvent): Future[Unit] = {
    val watches = evt.typ match {
      case EventType.None =>
        Seq(
          watchesFor(dataTable, None),
          watchesFor(existTable, None),
          watchesFor(childTable, None)
        ).flatten

      case EventType.NodeDataChanged | EventType.NodeCreated =>
        Seq(
          watchesFor(dataTable, evt.path),
          watchesFor(existTable, evt.path)
        ).flatten

      case EventType.NodeChildrenChanged =>
        watchesFor(childTable, evt.path)

      case EventType.NodeDeleted =>
        Seq(
          watchesFor(dataTable, evt.path),
          watchesFor(existTable, evt.path),
          watchesFor(childTable, evt.path)
        ).flatten

      case _ =>
        Seq.empty[Promise[WatchedEvent]]
    }

    watches foreach { _.setValue(evt) }

    Future.Done
  }

  private[this] def watchesFor(
    table: mutable.Map[String, Promise[WatchedEvent]],
    path: Option[String]
  ): Seq[Promise[WatchedEvent]] =
    table.synchronized {
      path match {
        case Some(p) => table.remove(p).toSeq
        case None =>
          val watches = table.values.toList
          table.clear()
          watches
      }
    }

  def existsWatch(path: String): Future[WatchedEvent] =
    existTable.synchronized { existTable.getOrElseUpdate(path, new Promise[WatchedEvent]) }

  def dataWatch(path: String): Future[WatchedEvent] =
    dataTable.synchronized { dataTable.getOrElseUpdate(path, new Promise[WatchedEvent]) }

  def childrenWatch(path: String): Future[WatchedEvent] =
    childTable.synchronized { childTable.getOrElseUpdate(path, new Promise[WatchedEvent]) }
}

case class WatchedEvent(typ: EventType, state: KeeperState, path: Option[String] = None)
object WatchedEvent {
  def apply(evt: WatcherEvent): WatchedEvent =
    WatchedEvent(EventType(evt.typ), KeeperState(evt.state), Option(evt.path))
}

sealed abstract class EventType(val code: Int)
object EventType {
  object None extends EventType(-1)
  object NodeCreated extends EventType(1)
  object NodeDeleted extends EventType(2)
  object NodeDataChanged extends EventType(3)
  object NodeChildrenChanged extends EventType(4)
  object DataWatchRemoved extends EventType(5)
  object ChildWatchRemoved extends EventType(6)

  def apply(code: Int) = code match {
    case -1 => None
    case 1 => NodeCreated
    case 2 => NodeDeleted
    case 3 => NodeDataChanged
    case 4 => NodeChildrenChanged
    case 5 => DataWatchRemoved
    case 6 => ChildWatchRemoved
  }
}

sealed abstract class KeeperState(val code: Int)
object KeeperState {
  /** Unused, this state is never generated by the server */
  @Deprecated
  object Unknown extends KeeperState(-1)

  /** The client is in the disconnected state - it is not connected
   * to any server in the ensemble. */
  object Disconnected extends KeeperState(0)

  /** Unused, this state is never generated by the server */
  @Deprecated
  object NoSyncConnected extends KeeperState(1)

  /** The client is in the connected state - it is connected
   * to a server in the ensemble (one of the servers specified
   * in the host connection parameter during ZooKeeper client
   * creation). */
  object SyncConnected extends KeeperState(3)

  /**
   * Auth failed state
   */
  object AuthFailed extends KeeperState(4)

  /**
   * The client is connected to a read-only server, that is the
   * server which is not currently connected to the majority.
   * The only operations allowed after receiving this state is
   * read operations.
   * This state is generated for read-only clients only since
   * read/write clients aren't allowed to connect to r/o servers.
   */
  object ConnectedReadOnly extends KeeperState(5)

  /**
    * SaslAuthenticated: used to notify clients that they are SASL-authenticated,
    * so that they can perform Zookeeper actions with their SASL-authorized permissions.
    */
  object SaslAuthenticated extends KeeperState(6)

  /** The serving cluster has expired this session. The ZooKeeper
   * client connection (the session) is no longer valid. You must
   * create a new client connection (instantiate a new ZooKeeper
   * instance) if you with to access the ensemble. */
  object Expired extends KeeperState(-112)

  def apply(code: Int): KeeperState = code match {
    case -1 => Unknown
    case 0 => Disconnected
    case 1 => NoSyncConnected
    case 3 => SyncConnected
    case 4 => AuthFailed
    case 5 => ConnectedReadOnly
    case 6 => SaslAuthenticated
    case -112 => Expired
  }
}

sealed abstract class KeeperException(val code: Int) extends Exception with NoStacktrace
object KeeperException {
  object Ok extends KeeperException(0)
  object SystemError extends KeeperException(-1)
  object RuntimeInconsistency extends KeeperException(-2)
  object DataInconsistency extends KeeperException(-3)
  object ConnectionLoss extends KeeperException(-4)
  object MarshallingError extends KeeperException(-5)
  object Unimplemented extends KeeperException(-6)
  object OperationTimeout extends KeeperException(-7)
  object BadArguments extends KeeperException(-8)
  object UnknownSession extends KeeperException(-12)
  object NewConfigNoQuorum extends KeeperException(-13)
  object ReconfigInProgress extends KeeperException(-14)
  object APIError extends KeeperException(-100)
  object NoNode extends KeeperException(-101)
  object NoAuth extends KeeperException(-102)
  object BadVersion extends KeeperException(-103)
  object NoChildrenForEphemerals extends KeeperException(-108)
  object NodeExists extends KeeperException(-110)
  object NotEmpty extends KeeperException(-111)
  object SessionExpired extends KeeperException(-112)
  object InvalidCallback extends KeeperException(-113)
  object InvalidACL extends KeeperException(-114)
  object AuthFailed extends KeeperException(-115)
  object SessionMoved extends KeeperException(-118)
  object NotReadOnly extends KeeperException(-119)
  object EphemeralOnLocalSession extends KeeperException(-120)
  object NoWatcher extends KeeperException(-121)

  def apply(code: Int): KeeperException = code match {
    case 0 => Ok
    case -1 => SystemError
    case -2 => RuntimeInconsistency
    case -3 => DataInconsistency
    case -4 => ConnectionLoss
    case -5 => MarshallingError
    case -7 => OperationTimeout
    case -8 => BadArguments
    case -12 => UnknownSession
    case -13 => NewConfigNoQuorum
    case -14 => ReconfigInProgress
    case -100 => APIError
    case -101 => NoNode
    case -102 => NoAuth
    case -103 => BadVersion
    case -108 => NoChildrenForEphemerals
    case -110 => NodeExists
    case -111 => NotEmpty
    case -112 => SessionExpired
    case -113 => InvalidCallback
    case -114 => InvalidACL
    case -115 => AuthFailed
    case -118 => SessionMoved
    case -119 => NotReadOnly
    case -120 => EphemeralOnLocalSession
    case -121 => NoWatcher
  }

  def unapply(code: Int): Option[KeeperException] =
    try { Some(apply(code)) } catch { case _: Throwable => None }
}
