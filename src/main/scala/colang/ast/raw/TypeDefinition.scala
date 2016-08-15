package colang.ast.raw

import colang.Strategy.Result
import colang.Strategy.Result.{Malformed, NoMatch, Success}
import colang.ast.raw.ParserImpl.{identifierStrategy, SingleTokenStrategy}
import colang.{SourceCode, TokenStream}
import colang.tokens._

/**
  * Represents a type definition.
  * @param specifiers type specifiers list
  * @param keyword 'struct' (or eventually 'class') keyword
  * @param name type name
  * @param body type body, may be absent
  */
case class TypeDefinition(specifiers: SpecifiersList,
                          keyword: Keyword,
                          name: Identifier,
                          body: Option[TypeBody]) extends GlobalSymbolDefinition {

  def source: SourceCode = specifiers.source + body.getOrElse(name).source
}

object TypeDefinition {
  val strategy = new ParserImpl.Strategy[TypeDefinition] {

    private val specifiersStrategy = new SpecifiersList.Strategy(
      classOf[NativeKeyword])

    def apply(stream: TokenStream): Result[TokenStream, TypeDefinition] = {
      ParserImpl.parseGroup()
        .element(specifiersStrategy,                          "type specifiers")
        .element(SingleTokenStrategy(classOf[StructKeyword]), "struct keyword",  stopIfAbsent = true)
        .element(identifierStrategy,                          "type name")
        .element(TypeBody.strategy,                           "type body",       optional = true)
        .parse(stream)
        .as[SpecifiersList, Keyword, Identifier, TypeBody] match {

        case (Some(specifiersList), Some(keyword), Some(name), bodyOption, issues, newStream) =>
          Success(TypeDefinition(specifiersList, keyword, name, bodyOption), issues, newStream)
        case (_, Some(keyword), None, _, issues, newStream) =>
          Malformed(issues, newStream)
        case _ => NoMatch()
      }
    }
  }
}

/**
  * Represents a type body, which can currently only contain instance methods.
  * @param leftBrace opening brace
  * @param methods type methods
  * @param rightBrace closing brace
  */
case class TypeBody(leftBrace: LeftBrace, methods: Seq[FunctionDefinition], rightBrace: RightBrace) extends Node {
  def source: SourceCode = leftBrace.source + rightBrace.source
}

object TypeBody {
  val strategy = new ParserImpl.Strategy[TypeBody] {

    def apply(stream: TokenStream): Result[TokenStream, TypeBody] = {
      ParserImpl.parseEnclosedSequence(
        stream = stream,
        sequenceDescription = "type body",
        elementStrategy = FunctionDefinition.strategy,
        elementDescription = "method definition",
        openingElement = classOf[LeftBrace],
        openingElementDescription = "opening '{'",
        closingElement = classOf[RightBrace],
        closingElementDescription = "closing '}'"
      ) match {
        case Some((leftBrace, elements, rightBraceOption, issues, streamAfterBlock)) =>
          val rightBrace = rightBraceOption match {
            case Some(rb) => rb
            case None =>
              val previousSource = if (elements.nonEmpty) elements.last.source else leftBrace.source
              RightBrace(previousSource.after)
          }

          Success(TypeBody(leftBrace, elements, rightBrace), issues, streamAfterBlock)
        case None => NoMatch()
      }
    }
  }
}