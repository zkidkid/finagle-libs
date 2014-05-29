package com.twitter.finagle.zookeeper.protocol

object XID {
  val Ping = -2
  val Auth = -4
  val Notification = -1
}

object OpCodes {
  val Notification = 0
  val Create = 1
  val Delete = 2
  val Exists = 3
  val GetData = 4
  val SetData = 5
  val GetACL = 6
  val SetACL = 7
  val GetChildren = 8
  val Sync = 9
  val Ping = 11
  val GetChildren2 = 12
  val Check = 13
  val Multi = 14
  val Create2 = 15
  val Reconfig = 16
  val CheckWatches = 17
  val RemoveWatches = 18
  val Auth = 100
  val SetWatches = 101
  val Sasl = 102
  val CreateSession = -10
  val CloseSession = -11
  val Error = -1
}
