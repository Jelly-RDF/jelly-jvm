package eu.neverblink.jelly.core.helpers

import java.io.OutputStream

object TestIoUtil:
  /**
   * Whether to silence output during tests.
   */
  val isOutputSilenced: Boolean = System.getenv("JELLY_TEST_SILENCE_OUTPUT") == "true"

  /**
   * If `JELLY_TEST_SILENCE_OUTPUT` env var is set to true, this method will execute the block of 
   * code with the standard output and error streams redirected to null, effectively silencing any output.
   * 
   * This is used in CI to prevent cluttering the logs with unnecessary output during tests.
   * 
   * @param block the block of code to execute with silenced output
   * @tparam T the return type of the block
   * @return
   */
  def withSilencedOutput[T](block: => T): T =
    if isOutputSilenced then
      Console.withOut(OutputStream.nullOutputStream) {
        Console.withErr(OutputStream.nullOutputStream) {
          block
        }
      }
    else block
