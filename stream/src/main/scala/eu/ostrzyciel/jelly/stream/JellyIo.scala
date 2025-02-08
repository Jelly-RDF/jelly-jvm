package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.stream.impl.JellyIoOps.*

/**
 * Convenience methods for working with Jelly over IO streams.
 */
object JellyIo extends FlowFromFrames, FlowToFrames, FrameSource, FrameSink
