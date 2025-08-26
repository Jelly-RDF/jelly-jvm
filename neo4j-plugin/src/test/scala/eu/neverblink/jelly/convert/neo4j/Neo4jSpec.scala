package eu.neverblink.jelly.convert.neo4j

import org.neo4j.driver.GraphDatabase
import org.neo4j.harness.{Neo4j, Neo4jBuilders}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Using

abstract class Neo4jSpec extends AnyWordSpec, Matchers, BeforeAndAfterAll:
  var neo4j: Neo4j = _

  /** Override this to register user functions in the test database.
    */
  protected val functions: Seq[Class[?]] = Seq()

  /** Override this to register aggregation functions in the test database.
    */
  protected val aggregations: Seq[Class[?]] = Seq()

  /** Override this to register procedures in the test database.
    */
  protected val procedures: Seq[Class[?]] = Seq()

  /** Helper to get a Neo4j session and run some code with it.
    * @param testCode
    *   code to run with the session
    * @tparam T
    *   return type of the code
    * @return
    */
  protected def withSession[T](testCode: org.neo4j.driver.Session => T): T =
    Using.resource(GraphDatabase.driver(neo4j.boltURI())) { driver =>
      Using.resource(driver.session()) { session =>
        testCode(session)
      }
    }

  override def beforeAll(): Unit =
    val builder = Neo4jBuilders.newInProcessBuilder()
      .withDisabledServer()
    for cls <- functions do builder.withFunction(cls)
    for cls <- aggregations do builder.withAggregationFunction(cls)
    for cls <- procedures do builder.withProcedure(cls)
    neo4j = builder.build()

  override def afterAll(): Unit =
    if neo4j != null then neo4j.close()
