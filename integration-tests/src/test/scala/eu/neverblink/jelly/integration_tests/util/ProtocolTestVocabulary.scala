package eu.neverblink.jelly.integration_tests.util

import org.apache.jena.rdf.model.{Model, Property, Resource, ResourceFactory}
import org.apache.jena.vocabulary.{RDF, RDFS}

import scala.jdk.CollectionConverters.*

object ProtocolTestVocabulary:
  val testsPrefix = "https://w3id.org/jelly/dev/tests"
  val manifestPrefix = "http://www.w3.org/2001/sw/DataAccess/tests/test-manifest#"
  val testEntryPrefix = "https://w3id.org/jelly/dev/tests/vocab#"
  val rdftPrefix = "http://www.w3.org/ns/rdftest#"

  val manifestEntriesProperty: Property = ResourceFactory.createProperty(manifestPrefix, "entries")
  val manifestTypeProperty: Property = ResourceFactory.createProperty(manifestPrefix, "Manifest")

  val testEntryTestPositiveProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "TestPositive")
  val testEntryTestNegativeProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "TestNegative")
  
  val testEntryTestRdfToJellyProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "TestRdfToJelly")
  val testEntryTestRdfFromJellyProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "TestRdfFromJelly")
  
  val testEntryNameProperty: Property = ResourceFactory.createProperty(manifestPrefix, "name")
  val testEntryCommentProperty: Property = ResourceFactory.createProperty(RDFS.uri, "comment")
  
  val testEntryRequiresProperty: Property = ResourceFactory.createProperty(manifestPrefix, "requires")
  
  val testEntryRequirementPhysicalTypeTriplesProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "requirementPhysicalTypeTriples")
  val testEntryRequirementPhysicalTypeQuadsProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "requirementPhysicalTypeQuads")
  val testEntryRequirementPhysicalTypeGraphsProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "requirementPhysicalTypeGraphs")
  val testEntryRequirementRdfStarProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "requirementRdfStar")
  val testEntryRequirementGeneralizedStatementsProperty: Property = ResourceFactory.createProperty(testEntryPrefix, "requirementGeneralizedRdf")
  
  val testEntryActionProperty: Property = ResourceFactory.createProperty(manifestPrefix, "action")
  val testEntryResultProperty: Property = ResourceFactory.createProperty(manifestPrefix, "result")

  val testEntryApprovalProperty: Property = ResourceFactory.createProperty(rdftPrefix, "approval")

  val testEntryApprovalRejectedProperty: Property = ResourceFactory.createProperty(rdftPrefix, "Rejected")

  extension (model: Model)
    def extractTestEntries: List[Resource] =
      model
        .listResourcesWithProperty(RDF.`type`, manifestTypeProperty)
        .nextOptional()
        .map(manifest => manifest.getProperty(manifestEntriesProperty).getList)
        .map(_.iterator.asScala.map(_.asResource).toList)
        .orElse(Nil)

  extension (resource: Resource)
    def extractTestUri: String =
      resource.getURI.stripPrefix(testsPrefix + "/")

    def extractTestName: String =
      Option(resource.getProperty(testEntryNameProperty))
        .map(_.getString)
        .getOrElse("No name provided")

    def extractTestComment: String =
      Option(resource.getProperty(testEntryCommentProperty))
        .map(_.getString)
        .getOrElse("No comment provided")

    def isTestPositive: Boolean =
      resource.hasProperty(RDF.`type`, testEntryTestPositiveProperty)

    def isTestNegative: Boolean =
      resource.hasProperty(RDF.`type`, testEntryTestNegativeProperty)

    def isTestRdfToJelly: Boolean =
      resource.hasProperty(RDF.`type`, testEntryTestRdfToJellyProperty)

    def isTestRdfFromJelly: Boolean =
      resource.hasProperty(RDF.`type`, testEntryTestRdfFromJellyProperty)

    def hasPhysicalTypeTriplesRequirement: Boolean =
      resource.hasProperty(testEntryRequiresProperty, testEntryRequirementPhysicalTypeTriplesProperty)

    def hasPhysicalTypeQuadsRequirement: Boolean =
      resource.hasProperty(testEntryRequiresProperty, testEntryRequirementPhysicalTypeQuadsProperty)

    def hasPhysicalTypeGraphsRequirement: Boolean =
      resource.hasProperty(testEntryRequiresProperty, testEntryRequirementPhysicalTypeGraphsProperty)

    def hasRdfStarRequirement: Boolean =
      resource.hasProperty(testEntryRequiresProperty, testEntryRequirementRdfStarProperty)

    def hasGeneralizedStatementsRequirement: Boolean =
      resource.hasProperty(testEntryRequiresProperty, testEntryRequirementGeneralizedStatementsProperty)

    def extractTestRequirements: Set[Resource] =
      resource
        .listProperties(testEntryRequiresProperty)
        .asScala
        .map(_.getResource)
        .filter(_ != null)
        .toSet

    def extractTestActions: List[String] =
      val property = resource.getProperty(testEntryActionProperty)
      if property.getResource.isURIResource then
        // Single action, not a list
        return List(property.getResource.getURI.stripPrefix(testsPrefix + "/"))

      resource
        .getProperty(testEntryActionProperty)
        .getList
        .iterator
        .asScala
        .map(_.asResource)
        .map(_.getURI.stripPrefix(testsPrefix + "/"))
        .toList

    def extractTestResults: List[String] = {
      val resultProperty = resource.getProperty(testEntryResultProperty)
      if resultProperty == null then
        return Nil

      if resultProperty.getResource.isURIResource then
        // Single result, not a list
        return List(resultProperty.getResource.getURI.stripPrefix(testsPrefix + "/"))

      resultProperty // Multiple results
        .getList
        .iterator
        .asScala
        .map(_.asResource)
        .map(_.getURI.stripPrefix(testsPrefix + "/"))
        .toList
    }

    def isTestRejected: Boolean =
      resource.hasProperty(testEntryApprovalProperty, testEntryApprovalRejectedProperty)
  