package eu.ostrzyciel.jelly.stream

import eu.ostrzyciel.jelly.core.IterableAdapter
import eu.ostrzyciel.jelly.stream.impl.RdfSourceBuilderImpl

object RdfSource:
  def builder[TGraph, TDataset, TNode, TTriple, TQuad]
  (using adapter: IterableAdapter[TNode, TTriple, TQuad, TGraph, TDataset]):
  RdfSourceBuilderImpl[TGraph, TDataset, TNode, TTriple, TQuad]#BaseBuilder =
    new RdfSourceBuilderImpl().builder
