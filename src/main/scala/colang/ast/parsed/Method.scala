package colang.ast.parsed

import colang.ast.raw

/**
  * Represents a method: a function that can only be called on an instance of some type.
  * Note that a method is not a symbol because it isn't stable.
  * @param name method name
  * @param container containing type
  * @param returnType method return type
  * @param parameters method parameters
  * @param body method body
  * @param native whether method is native
  */
class Method(val name: String,
             val container: Type,
             val returnType: Type,
             val parameters: Seq[Variable],
             val body: CodeBlock,
             val definition: Option[raw.FunctionDefinition],
             val native: Boolean = false) extends Applicable {

  val definitionSite = definition match {
    case Some(fd) => Some(fd.prototypeSource)
    case None => None
  }

  def signatureString: String = {
    val paramString = parameters map { _.type_.qualifiedName } mkString ", "
    s"${returnType.qualifiedName} ${container.qualifiedName}.$name($paramString)"
  }
}
