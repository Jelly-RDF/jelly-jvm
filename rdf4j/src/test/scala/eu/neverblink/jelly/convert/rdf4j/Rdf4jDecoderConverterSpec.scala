package eu.neverblink.jelly.convert.rdf4j

import org.eclipse.rdf4j.model.base.CoreDatatype
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Rdf4jDecoderConverterSpec extends AnyWordSpec, Matchers {
  "Rdf4jDecoderConverter" should {
    "construct core datatypes that have reference-equal IRIs" in {
      val converter = Rdf4jDecoderConverter()
      val dt1 = converter.makeDatatype("http://www.w3.org/2001/XMLSchema#string")

      // This assertion is called in RDF4J when creating typed literals...
      dt1.dt() should be theSameInstanceAs dt1.coreDatatype().getIri

      // Subsequent calls with the same IRI should return the same datatype instance
      val dt2 = converter.makeDatatype("http://www.w3.org/2001/XMLSchema#string")
      dt2.dt() should be theSameInstanceAs dt1.dt()
      dt2.coreDatatype().getIri should be theSameInstanceAs dt1.coreDatatype.getIri
    }

    "construct non-core datatypes" in {
      val converter = Rdf4jDecoderConverter()
      val dt1 = converter.makeDatatype("http://example.com/datatype")

      dt1.dt().stringValue() shouldEqual "http://example.com/datatype"
      dt1.coreDatatype() should be theSameInstanceAs CoreDatatype.NONE
    }
  }
}
