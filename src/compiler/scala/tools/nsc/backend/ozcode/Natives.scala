package scala.tools.nsc
package backend
package ozcode

import scala.collection.mutable.HashMap

trait Natives { self: OzCodes =>
  import global.{ Symbol, definitions, stringToTermName }
  import ast._

  abstract class NativeMethod(val fullName: String, val resultTypeName: String,
      val argDefs: Pair[String, String]*) {
    def this(fullName: String, argDefs: Pair[String, String]*) =
      this(fullName, null:String, argDefs:_*)

    val hash = paramsHash(argDefs.toList map (_._2), resultTypeName)

    def body: ast.Phrase
  }

  abstract class SpecializedNativeMethod(val fullName: String,
      val resultTypeName: String, val argDefs: Pair[String, String]*) {
    def specializedKinds = List(UnitKind, BooleanKind, CharKind, ByteKind,
        ShortKind, IntKind, LongKind, FloatKind, DoubleKind, RefKind)

    def body(specializedKind: TypeKind): ast.Phrase

    def specializations: List[NativeMethod] = {
      for (specializedKind <- specializedKinds) yield
        makeSpecializedMethod(specializedKind)
    }

    private def makeSpecializedMethod(specializedKind: TypeKind) = {
      val specializedName = specializedKind.toType.typeSymbol.fullName
      val specializedParamName =
        if (specializedKind == UnitKind) "scala.runtime.BoxedUnit"
        else specializedName

      def mapTypeName(typeName: String, param: Boolean) = {
        if (typeName == "A")
          if (param) specializedParamName else specializedName
        else
          typeName
      }

      val name =
        if (specializedKind.isInstanceOf[REFERENCE])
          fullName
        else
          fullName + "$m" + specializedKind.primitiveCharCode + "c$sp"

      val result = mapTypeName(resultTypeName, param = false)
      val args = argDefs map {
        case (name, typeName) => (name, mapTypeName(typeName, param = true))
      }

      new Method(specializedKind, name, result, args:_*)
    }

    private class Method(specializedKind: TypeKind, fullName: String,
        resultTypeName: String, argDefs: Pair[String, String]*) extends
        NativeMethod(fullName, resultTypeName, argDefs:_*) {
      def body = SpecializedNativeMethod.this.body(specializedKind)
    }
  }

  object nativeMethods {
    private val methods = new HashMap[Pair[String, Int], NativeMethod]

    private[this] def register(native: NativeMethod) {
      methods += (native.fullName, native.hash) -> native
    }

    private[this] def register(native: SpecializedNativeMethod) {
      for (specialized <- native.specializations)
        register(specialized)
    }

    def init() {
      methods.clear()

      register(ScalaOzma_newUnbound)
      register(ScalaOzma_waitBound)
      register(ScalaOzma_waitQuiet)
      register(ScalaOzma_waitNeeded)
      register(ScalaOzma_byNeed)
      register(ScalaOzma_byNeedFuture)
      register(ScalaOzma_makeFailedValue)
      register(ScalaOzma_sleep)

      register(Integer_toString)
      register(Float_toString)
      register(Character_toString)
      register(Integer_parseInt)
      register(Float_parseFloat)

      register(Boolean_box)
      register(Char_box)
      register(Byte_box)
      register(Short_box)
      register(Int_box)
      register(Long_box)
      register(Float_box)
      register(Double_box)

      register(Boolean_unbox)
      register(Char_unbox)
      register(Byte_unbox)
      register(Short_unbox)
      register(Int_unbox)
      register(Long_unbox)
      register(Float_unbox)
      register(Double_unbox)

      register(System_currentTimeMillis)
      register(System_arraycopy)
      register(System_identityHashCode)

      register(Runtime_halt0)
      register(Runtime_gc)

      register(Array_newArray)
      register(Array_multiNewArray)
      register(Array_getLength)
      register(Array_get)
      register(Array_getBoolean)
      register(Array_getByte)
      register(Array_getChar)
      register(Array_getShort)
      register(Array_getInt)
      register(Array_getLong)
      register(Array_getFloat)
      register(Array_getDouble)
      register(Array_set)
      register(Array_setBoolean)
      register(Array_setByte)
      register(Array_setChar)
      register(Array_setShort)
      register(Array_setInt)
      register(Array_setLong)
      register(Array_setFloat)
      register(Array_setDouble)

      register(StandardOutPrintStream_writeString)
      register(StandardErrPrintStream_writeString)

      register(Port_newPort)
      register(Port_send)

      register(ResultPort_newPort)
      register(ResultPort_send)
      register(ResultPort_newActiveObject)

      register(Random_rand)
    }

    def getBodyFor(symbol: Symbol) = {
      val key = (symbol.fullName, paramsHash(symbol))

      methods.get(key) match {
        case Some(method) => method.body
        case None =>
          Console.println("Warning: could not find native definition for")
          Console.println(key)
          null
      }
    }
  }

  object astDSL {
    implicit def char2literal(value: Char) = IntLiteral(value)
    implicit def int2literal(value: Int) = IntLiteral(value)
    implicit def float2literal(value: Double) = FloatLiteral(value)
    implicit def bool2literal(value: Boolean) = Bool(value)
    implicit def symbol2atom(value: scala.Symbol) = Atom(value.name)

    implicit def pair2colonInt(pair: Pair[Int, Phrase]) = pair match {
      case Pair(feature, phrase) => Colon(feature, phrase)
    }

    implicit def pair2colonSym(pair: Pair[scala.Symbol, Phrase]) = pair match {
      case Pair(feature, phrase) => Colon(feature, phrase)
    }

    def unit = ast.UnitVal()
    def nil = ast.Atom("nil")

    def __ = ast.Wildcard()
    def $ = ast.Dollar()

    // Method

    abstract class BaseMethodWrapper(val obj: Phrase, val name: String) {
      protected def hash: Int

      def apply(args: Phrase*) = {
        val label = Atom(if (hash == 0) name else name + "#" + hash)
        val message = buildMessage(label, args.toList)

        Apply(obj, List(message))
      }
    }

    class MethodWrapper(obj: Phrase, name: String,
        val paramTypes: String*) extends BaseMethodWrapper(obj, name) {
      protected def hash = paramsHash(paramTypes.toList)
    }

    class NativeMethodWrapper(obj: Phrase,
        name: String) extends BaseMethodWrapper(obj, name) {
      protected def hash = 0
    }

    // Wrapper

    class PhraseWrapper(phrase: Phrase) {
      def ===(right: Phrase) = Eq(phrase, right)

      def ==>(right: Phrase) = CaseClause(phrase, right)

      def ::(left: Phrase) = Tuple(Atom("|"), left, phrase)

      def ~>(right: Phrase) = BinaryOpApply(".", phrase, right)

      def + (right: Phrase) = BinaryOpApply("+", phrase, right)
      def - (right: Phrase) = BinaryOpApply("-", phrase, right)
      def * (right: Phrase) = BinaryOpApply("*", phrase, right)
      def / (right: Phrase) = BinaryOpApply("/", phrase, right)

      def div(right: Phrase) = BinaryOpApply("div", phrase, right)
      def mod(right: Phrase) = BinaryOpApply("mod", phrase, right)

      def toRawVS = new MethodWrapper(phrase, "toRawVS")
      def toRawString = new MethodWrapper(phrase, "toRawString")

      def identity = new MethodWrapper(phrase, "identity")

      def doApply = new MethodWrapper(phrase, "apply", "java.lang.Object")

      def apply0(resultKind: TypeKind) = {
        if (resultKind.isInstanceOf[REFERENCE])
          doApply
        else {
          val name = "apply$mc" + resultKind.primitiveCharCode + "$sp"
          new MethodWrapper(phrase, name, resultKind.toType.typeSymbol.fullName)
        }
      }

      def newArrayOfThisClass = new NativeMethodWrapper(phrase,
          "newArrayOfThisClass")
      def multiNewArrayOfThisClass = new NativeMethodWrapper(phrase,
          "multiNewArrayOfThisClass")
      def length = new NativeMethodWrapper(phrase, "length")
      def get = new NativeMethodWrapper(phrase, "get")

      def put = new {
        def apply(index: Phrase, value: Phrase) =
          Apply(phrase, List('put(index, value)))
      }
    }

    implicit def wrapPhrase(phrase: Phrase) = new PhraseWrapper(phrase)
    implicit def wrapSymbol(symbol: scala.Symbol) =
      new PhraseWrapper(symbol2atom(symbol))

    // Builtin functions

    case class BuiltinFunction(fun: Phrase) {
      def apply(params: Phrase*) = Apply(fun, params.toList)
    }

    def Wait = BuiltinFunction(Variable("Wait"))
    def WaitNeeded = BuiltinFunction(Variable("WaitNeeded"))
    def ByNeed = BuiltinFunction(Variable("ByNeed"))
    def ByNeedFuture = BuiltinFunction(Variable("ByNeedFuture"))
    def IsDet = BuiltinFunction(Variable("IsDet"))
    def Delay = BuiltinFunction(Variable("Delay"))
    def IntToString = BuiltinFunction(Variable("IntToString"))
    def FloatToString = BuiltinFunction(Variable("FloatToString"))
    def StringToInt = BuiltinFunction(Variable("StringToInt"))
    def StringToFloat = BuiltinFunction(Variable("StringToFloat"))
    def StringLiteral = BuiltinFunction(Variable("StringLiteral"))
    def NewPort = BuiltinFunction(Variable("NewPort"))
    def Send = BuiltinFunction(Variable("Send"))
    def NewOzmaPort = BuiltinFunction(Variable("NewOzmaPort"))
    def NewOzmaResultPort = BuiltinFunction(Variable("NewOzmaResultPort"))
    def NewActiveObject = BuiltinFunction(Variable("NewActiveObject"))
    def Throw = BuiltinFunction(Variable("Throw"))
    def ArrayCopy = BuiltinFunction(Variable("ArrayCopy"))

    // System module

    object System {
      @inline private[this] def System = Variable("System")

      def printInfo = BuiltinFunction(System ~> 'printInfo)
      def showInfo = BuiltinFunction(System ~> 'showInfo)
      def printError = BuiltinFunction(System ~> 'printError)
      def showError = BuiltinFunction(System ~> 'showError)

      def gcDo = BuiltinFunction(System ~> 'gcDo)
    }

    // Value module

    object Value {
      @inline private[this] def Value = Variable("Value")

      def waitQuiet = BuiltinFunction(Value ~> 'waitQuiet)
      def status = BuiltinFunction(Value ~> 'status)
      def makeNeeded = BuiltinFunction(Value ~> 'makeNeeded)
      def failed = BuiltinFunction(Value ~> 'failed)
    }

    // OS module

    object OS {
      @inline private[this] def OS = Variable("OS")

      def rand = BuiltinFunction(OS ~> 'rand)
      def time = BuiltinFunction(OS ~> 'time)
    }

    // Application module

    object Application {
      @inline private[this] def Application = Variable("Application")

      def exit = BuiltinFunction(Application ~> 'exit)
    }

    // New constructor call

    def New(clazz: Symbol, arguments: Pair[Phrase, String]*) = {
      val arguments1 = arguments.toList
      val actualArgs = arguments1 map (_._1)
      val paramTypeNames = arguments1 map (_._2)
      genNew(clazz, actualArgs,
          atomForSymbol("<init>", paramsHash(paramTypeNames, clazz.fullName)))
    }

    def New(className: String, arguments: Pair[Phrase, String]*): Phrase =
      New(definitions.getClass(className toTypeName), arguments:_*)
  }

  import astDSL._
  import astDSL.StringLiteral

  object ScalaOzma_newUnbound extends
      SpecializedNativeMethod("scala.ozma.package.newUnbound", "A") {
    def body(specializedKind: TypeKind) = __
  }

  object ScalaOzma_waitBound extends SpecializedNativeMethod(
      "scala.ozma.package.waitBound", "scala.Unit", "`value`" -> "A") {
    def body(specializedKind: TypeKind) = {
      And(
          Wait(QuotedVar("value")),
          unit
      )
    }
  }

  object ScalaOzma_waitQuiet extends SpecializedNativeMethod(
      "scala.ozma.package.waitQuiet", "scala.Unit", "`value`" -> "A") {
    def body(specializedKind: TypeKind) = {
      And(
          Value.waitQuiet(QuotedVar("value")),
          unit
      )
    }
  }

  object ScalaOzma_waitNeeded extends SpecializedNativeMethod(
      "scala.ozma.package.waitNeeded", "scala.Unit", "`value`" -> "A") {
    def body(specializedKind: TypeKind) = {
      And(
          WaitNeeded(QuotedVar("value")),
          unit
      )
    }
  }

  object ScalaOzma_byNeed extends SpecializedNativeMethod(
      "scala.ozma.package.byNeed", "A",
      "`value`" -> "scala.Function0") {
    def body(specializedKind: TypeKind) = {
      val value = QuotedVar("value")
      ByNeed(Fun($, Nil, value.apply0(specializedKind)()))
    }
  }

  object ScalaOzma_byNeedFuture extends SpecializedNativeMethod(
      "scala.ozma.package.byNeedFuture", "A",
      "`value`" -> "scala.Function0") {
    def body(specializedKind: TypeKind) = {
      val value = QuotedVar("value")
      ByNeedFuture(Fun($, Nil, value.apply0(specializedKind)()))
    }
  }

  object ScalaOzma_makeFailedValue extends SpecializedNativeMethod(
      "scala.ozma.package.makeFailedValue", "A",
      "`throwable`" -> "java.lang.Throwable") {
    def body(specializedKind: TypeKind) = {
      Value.failed(QuotedVar("throwable"))
    }
  }

  object ScalaOzma_sleep extends NativeMethod(
      "scala.ozma.package.sleep", "scala.Unit",
      "`ms`" -> "scala.Int") {
    def body = {
      val ms = QuotedVar("ms")
      And(
          Delay(ms),
          unit
      )
    }
  }

  abstract class Number_toString(fullName: String) extends
      NativeMethod(fullName, "java.lang.String") {
    def ValueToString: BuiltinFunction

    def body = {
      def ValueField = QuotedVar("value ")
      def Raw = Variable("Raw")
      def Tail = Variable("Tail")

      LocalDef(
          Raw === ValueToString(At(ValueField)),
      // in
          Case(Raw, List(
              '~' :: Tail ==> StringLiteral('-' :: Tail)),
          // else
              StringLiteral(Raw)
          )
      )
    }
  }

  object Integer_toString extends Number_toString(
      "java.lang.Integer.toString") {
    def ValueToString = IntToString
  }

  object Float_toString extends Number_toString(
      "java.lang.Float.toString") {
    def ValueToString = FloatToString
  }

  object Character_toString extends NativeMethod(
      "java.lang.Character.toString", "java.lang.String") {
    def body = {
      val ValueField = QuotedVar(" value")

      StringLiteral(At(ValueField) :: nil)
    }
  }

  abstract class Number_parse(fullName: String, resultType: String)
      extends NativeMethod(fullName, resultType, "`s`" -> "java.lang.String") {
    def StringToValue: BuiltinFunction
    def ErrorIdentifier: Atom

    def body = {
      def S = QuotedVar("s")
      def Raw = Variable("Raw")
      def Tail = Variable("Tail")

      val tryBlock = LocalDef(
          Raw === S.toRawString(),
      // in
          Case(Raw, List(
              '-' :: Tail ==> StringToValue('~' :: Tail)),
          // else
              StringToValue(Raw)
          )
      )

      Try(
          tryBlock,
      Catch(List(
          'error('kernel(ErrorIdentifier, __), 'debug -> __) ==>
              And(Throw(New("java.lang.NumberFormatException")), unit)
      )), NoFinally())
    }
  }

  object Integer_parseInt extends Number_parse(
      "java.lang.Integer.parseInt", "scala.Int") {
    def StringToValue = StringToInt
    def ErrorIdentifier = Atom("stringNoInt")
  }

  object Float_parseFloat extends Number_parse(
      "java.lang.Float.parseFloat", "scala.Float") {
    def StringToValue = StringToFloat
    def ErrorIdentifier = Atom("stringNoFloat")
  }

  abstract class Primitive_box(primitiveType: String, boxedType: String)
      extends NativeMethod(primitiveType+".box", boxedType,
          "`x`" -> primitiveType) {

    def body = {
      def BoxedType = Apply(QuotedVar("module:"+boxedType+"$"), Nil)

      implicit def wrapForValueOf(phrase: Phrase) = new {
        def valueOf = new MethodWrapper(phrase, "valueOf",
            primitiveType, boxedType)
      }

      def x = QuotedVar("x")
      def R = Variable("R")

      Case(Value.status(x), List(
          'det(__) ==> BoxedType.valueOf(x),
          'failed ==> x
      ), // else
          LocalDef(
              R,
          And(// in
              Thread(And(
                  WaitNeeded(x),
                  Value.makeNeeded(R)
              )),
              R === ByNeedFuture(Fun($, Nil,
                  And(Wait(x), BoxedType.valueOf(x))
              )),
              R
          ))
      )
    }
  }

  object Boolean_box extends Primitive_box("scala.Boolean", "java.lang.Boolean")
  object Char_box extends Primitive_box("scala.Char", "java.lang.Character")
  object Byte_box extends Primitive_box("scala.Byte", "java.lang.Byte")
  object Short_box extends Primitive_box("scala.Short", "java.lang.Short")
  object Int_box extends Primitive_box("scala.Int", "java.lang.Integer")
  object Long_box extends Primitive_box("scala.Long", "java.lang.Long")
  object Float_box extends Primitive_box("scala.Float", "java.lang.Float")
  object Double_box extends Primitive_box("scala.Double", "java.lang.Double")

  abstract class Primitive_unbox(primitiveType: String,
      theValueMethod: String)
      extends NativeMethod(primitiveType+".unbox", primitiveType,
          "`x`" -> "java.lang.Object") {

    def body = {
      implicit def wrapForTheValue(phrase: Phrase) = new {
        def theValue = new MethodWrapper(phrase, theValueMethod, primitiveType)
      }

      def x = QuotedVar("x")
      def R = Variable("R")

      Case(Value.status(x), List(
          'det(__) ==> x.theValue(),
          'failed ==> x
      ), // else
          LocalDef(
              R,
          And(// in
              Thread(And(
                  WaitNeeded(x),
                  Value.makeNeeded(R)
              )),
              R === ByNeedFuture(Fun($, Nil, x.theValue())),
              R
          ))
      )
    }
  }

  object Boolean_unbox extends Primitive_unbox("scala.Boolean", "booleanValue")
  object Char_unbox extends Primitive_unbox("scala.Char", "charValue")
  object Byte_unbox extends Primitive_unbox("scala.Byte", "byteValue")
  object Short_unbox extends Primitive_unbox("scala.Short", "shortValue")
  object Int_unbox extends Primitive_unbox("scala.Int", "intValue")
  object Long_unbox extends Primitive_unbox("scala.Long", "longValue")
  object Float_unbox extends Primitive_unbox("scala.Float", "floatValue")
  object Double_unbox extends Primitive_unbox("scala.Double", "doubleValue")

  object System_currentTimeMillis extends NativeMethod(
      "java.lang.System.currentTimeMillis", "scala.Long") {
    def body = {
      OS.time() * 1000
    }
  }

  object System_arraycopy extends NativeMethod(
      "java.lang.System.arraycopy", "scala.Unit",
      "src" -> "java.lang.Object", "srcPos" -> "scala.Int",
      "dest" -> "java.lang.Object", "destPos" -> "scala.Int",
      "length" -> "scala.Int") {
    def body = {
      And(ArrayCopy(QuotedVar("src"), QuotedVar("srcPos"), QuotedVar("dest"),
          QuotedVar("destPos"), QuotedVar("length")), unit)
    }
  }

  object System_identityHashCode extends NativeMethod(
      "java.lang.System.identityHashCode", "scala.Int",
      "x" -> "java.lang.Object") {
    def body = {
      def x = QuotedVar("x")

      x.identity()
    }
  }

  object Runtime_halt0 extends NativeMethod("java.lang.Runtime.halt0",
      "scala.Unit", "status" -> "scala.Int") {
    def body = {
      def status = QuotedVar("status")

      And(Application.exit(status), unit)
    }
  }

  object Runtime_gc extends NativeMethod("java.lang.Runtime.gc", "scala.Unit") {
    def body = {
      And(System.gcDo(), unit)
    }
  }

  object Array_newArray extends NativeMethod("java.lang.reflect.Array.newArray",
      "java.lang.Object", "`componentType`" -> "java.lang.Class",
      "`length`" -> "scala.Int") {
    def body = {
      def componentType = QuotedVar("componentType")
      def length = QuotedVar("length")

      componentType.newArrayOfThisClass(length)
    }
  }

  object Array_multiNewArray extends NativeMethod(
      "java.lang.reflect.Array.multiNewArray",
      "java.lang.Object", "`componentType`" -> "java.lang.Class",
      "`dimensions`" -> "scala.Int[]") {
    def body = {
      def componentType = QuotedVar("componentType")
      def dimensions = QuotedVar("dimensions")

      componentType.multiNewArrayOfThisClass(dimensions)
    }
  }

  object Array_getLength extends NativeMethod(
      "java.lang.reflect.Array.getLength",
      "scala.Int", "`array`" -> "java.lang.Object") {
    def body = {
      def array = QuotedVar("array")

      array.length()
    }
  }

  abstract class ArrayGet(name: String,
      elementType: String) extends NativeMethod("java.lang.reflect.Array."+name,
      elementType, "`array`" -> "java.lang.Object", "`index`" -> "scala.Int") {
    def body = {
      def array = QuotedVar("array")
      def index = QuotedVar("index")

      array.get(index)
    }
  }

  object Array_get extends ArrayGet("get", "java.lang.Object")
  object Array_getBoolean extends ArrayGet("getBoolean", "scala.Boolean")
  object Array_getByte extends ArrayGet("getByte", "scala.Byte")
  object Array_getChar extends ArrayGet("getChar", "scala.Char")
  object Array_getShort extends ArrayGet("getShort", "scala.Short")
  object Array_getInt extends ArrayGet("getInt", "scala.Int")
  object Array_getLong extends ArrayGet("getLong", "scala.Long")
  object Array_getFloat extends ArrayGet("getFloat", "scala.Float")
  object Array_getDouble extends ArrayGet("getDouble", "scala.Double")

  abstract class ArrayPut(name: String,
      elementType: String) extends NativeMethod("java.lang.reflect.Array."+name,
      "scala.Unit", "`array`" -> "java.lang.Object", "`index`" -> "scala.Int",
      "`value`" -> elementType) {
    def body = {
      def array = QuotedVar("array")
      def index = QuotedVar("index")
      def value = QuotedVar("value")

      And(array.put(index, value), unit)
    }
  }

  object Array_set extends ArrayPut("set", "java.lang.Object")
  object Array_setBoolean extends ArrayPut("setBoolean", "scala.Boolean")
  object Array_setByte extends ArrayPut("setByte", "scala.Byte")
  object Array_setChar extends ArrayPut("setChar", "scala.Char")
  object Array_setShort extends ArrayPut("setShort", "scala.Short")
  object Array_setInt extends ArrayPut("setInt", "scala.Int")
  object Array_setLong extends ArrayPut("setLong", "scala.Long")
  object Array_setFloat extends ArrayPut("setFloat", "scala.Float")
  object Array_setDouble extends ArrayPut("setDouble", "scala.Double")

  object StandardOutPrintStream_writeString extends NativeMethod(
      "java.lang.StandardOutPrintStream.writeString",
      "scala.Unit", "`s`" -> "java.lang.String") {
    def body = {
      And(System.printInfo(QuotedVar("s").toRawVS()), unit)
    }
  }

  object StandardErrPrintStream_writeString extends NativeMethod(
      "java.lang.StandardErrPrintStream.writeString",
      "scala.Unit", "`s`" -> "java.lang.String") {
    def body = {
      And(System.printError(QuotedVar("s").toRawVS()), unit)
    }
  }

  object Port_newPort extends NativeMethod("ozma.Port.newPort",
      "scala.Tuple2") {
    def body = {
      NewOzmaPort()
    }
  }

  object Port_send extends NativeMethod("ozma.Port.send",
      "scala.Unit", "`element`" -> "java.lang.Object") {
    def body = {
      And(
          Send(At(QuotedVar("rawPort ")), QuotedVar("element")),
          unit
      )
    }
  }

  object ResultPort_newPort extends NativeMethod("ozma.ResultPort.newPort",
      "scala.Tuple2") {
    def body = {
      NewOzmaResultPort()
    }
  }

  object ResultPort_send extends NativeMethod("ozma.ResultPort.send",
      "java.lang.Object", "`element`" -> "java.lang.Object") {
    def body = {
      Send(At(QuotedVar("rawPort ")), Tuple(Atom("#"), QuotedVar("element"), $))
    }
  }

  object ResultPort_newActiveObject extends NativeMethod(
      "ozma.ResultPort.newActiveObject",
      "java.lang.Object", "`obj`" -> "java.lang.Object") {
    def body = {
      NewActiveObject(QuotedVar("obj"))
    }
  }

  object Random_rand extends NativeMethod("ozma.Random.rand", "scala.Int") {
    def body = {
      OS.rand()
    }
  }
}
