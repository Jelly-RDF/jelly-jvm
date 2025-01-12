package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.IterableAdapter

object RdfSource:
  def builder[TGraph, TDataset, TNode, TTriple, TQuad]
  (using adapter: IterableAdapter[TNode, TTriple, TQuad, TGraph, TDataset]):
  RdfSourceBuilderImpl[TGraph, TDataset, TNode, TTriple, TQuad]#BaseBuilder =
    new RdfSourceBuilderImpl().builder
