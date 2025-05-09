package eu.neverblink.jelly.stream

import eu.neverblink.jelly.stream.impl.JellyIoOps.*

/**
 * Convenience methods for working with Jelly over IO streams.
 */
object JellyIo extends FlowFromFrames, FlowToFrames, FrameSource, FrameSink
