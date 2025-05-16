package eu.neverblink.jelly.pekko.stream

import eu.neverblink.jelly.pekko.stream.impl.JellyIoOps.*

/**
 * Convenience methods for working with Jelly over IO streams.
 */
object JellyIo extends FlowFromFrames, FlowToFrames, FrameSource, FrameSink
