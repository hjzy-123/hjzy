package com.sk.hjzy.processor.stream

import java.nio.channels.Pipe

class PipeStream {

  private val pipe = Pipe.open()
  private val sink = pipe.sink()        //写
  private val source = pipe.source()    //读

  def getSink: Pipe.SinkChannel = sink
  def getSource: Pipe.SourceChannel = source

}
