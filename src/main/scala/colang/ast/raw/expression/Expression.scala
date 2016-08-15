package colang.ast.raw.expression

import colang.Strategy.Result
import colang.Strategy.Result.{NoMatch, Success}
import colang.ast.raw.statement.Statement
import colang.ast.raw.{Node, ParserImpl}
import colang.{SourceCode, StrategyUnion, TokenStream}

/**
  * Represents a node that can be evaluated, producing some value.
  */
trait Expression extends Statement

/**
  * Represents a postfix operator (unary like '++' or binary like function calls, array subscripts, etc.)
  */
trait PostfixOperator extends Node {

  /**
    * Applies the postfix operator to an expression.
    * @return postfix operator expression
    */
  def apply: Expression => Expression
}

object Expression {

  /**
    * A strategy for parsing primary expressions.
    * All expressions are primary except when the outermost node is a postfix operator or a binary infix operator.
    */
  val primaryStrategy = StrategyUnion(
    DoubleLiteral.strategy,
    SymbolReference.strategy)

  /**
    * A strategy for parsing secondary and primary expressions.
    * Secondary expressions are postfix operator expressions.
    */
  val secondaryStrategy = new ParserImpl.Strategy[Expression] {
    private val postfixOperatorStrategy = StrategyUnion(FunctionCall.strategy)

    private case class PostfixOperatorSequence(operators: ::[PostfixOperator]) extends Node {
      def source: SourceCode = operators.head.source + operators.last.source
    }

    private val postfixOperatorSequenceStrategy = new ParserImpl.Strategy[PostfixOperatorSequence] {
      override def apply(stream: TokenStream): Result[TokenStream, PostfixOperatorSequence] = {

        ParserImpl.parseSequence(
          stream = stream,
          elementStrategy = postfixOperatorStrategy,
          elementDescription = "postfix operator"
        ) match {
          case (operators, issues, streamAfterOps) if operators.nonEmpty =>
            Success(PostfixOperatorSequence(operators.toList.asInstanceOf[::[PostfixOperator]]), issues, streamAfterOps)
          case _ => NoMatch()
        }
      }
    }

    def apply(stream: TokenStream): Result[TokenStream, Expression] = {
      ParserImpl.parseGroup()
        .element(primaryStrategy,                 "expression",         stopIfAbsent = true)
        .element(postfixOperatorSequenceStrategy, "postfix operators",  optional = true)
        .parse(stream)
        .as[Expression, PostfixOperatorSequence] match {

        case (Some(expression), Some(PostfixOperatorSequence(postfixOps)), issues, streamAfterExpr) =>
          val transform = (postfixOps.reverse map { _.apply } foldLeft identity[Expression] _) { _ compose _ }
          val result = transform(expression)
          Success(result, issues, streamAfterExpr)

        case (Some(expression), None, issues, streamAfterExpr) => Success(expression, issues, streamAfterExpr)
        case _ => NoMatch()
      }
    }
  }

  /**
    * A strategy for parsing tertiary, secondary, and primary expressions.
    * Tertiary expressions are binary infix operator expressions.
    */
  val tertiaryStrategy = InfixOperator.strategy

  /**
    * A strategy for parsing any kind of expressions.
    * This object should be used in most cases.
    */
  val strategy = tertiaryStrategy
}