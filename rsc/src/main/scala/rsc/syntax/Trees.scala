// Copyright (c) 2017-2018 Twitter, Inc.
// Licensed under the Apache License, Version 2.0 (see LICENSE.md).
package rsc.syntax

import rsc.inputs._
import rsc.pretty._
import rsc.semantics._

sealed trait Tree extends Pretty with Product {
  var pos: Position = NoPosition
  def withPos(pos: Position): this.type = {
    this.pos = pos
    this
  }
  override def hashCode(): Int = System.identityHashCode(this)
  override def equals(that: Any) = this eq that.asInstanceOf[AnyRef]
  def printStr(p: Printer): Unit = PrettyTree.str(p, this)
  def printRepl(p: Printer): Unit = PrettyTree.repl(p, this)
  def scalaStr: String = {
    val p = new Printer
    PrettyTree.scalaStr(p, this)
    p.toString
  }
  def javaStr: String = {
    val p = new Printer
    PrettyTree.javaStr(p, this)
    p.toString
  }
}

final case class AnonId() extends Id

final case class Case(pat: Pat, cond: Option[Term], stats: List[Stat]) extends Tree

final case class CtorId() extends NamedId {
  def value = CtorId.value
  def name = CtorId.name
}

object CtorId {
  val value = "<init>"
  val name = TermName("<init>")
}

final case class DefnClass(
    mods: Mods,
    id: TptId,
    tparams: List[TypeParam],
    primaryCtor: Option[PrimaryCtor],
    earlies: List[Stat],
    inits: List[Init],
    self: Option[Self],
    stats: List[Stat])
    extends DefnTemplate
    with TypeOutline {
  def paramss = Nil
}

final case class DefnCtor(mods: Mods, id: CtorId, paramss: List[List[Param]], rhs: Term)
    extends DefnDef
    with TermOutline {
  def tparams = Nil
  def ret = Some(TptId("Unit").withSym(UnitClass))
}

sealed trait DefnDef extends Stat with Parameterized with TermOutline {
  def mods: Mods
  def id: NamedId
  def tparams: List[TypeParam]
  def paramss: List[List[Param]]
  def ret: Option[Tpt]
}

final case class DefnField(mods: Mods, id: TermId, tpt: Option[Tpt], rhs: Option[Term])
    extends Stat
    with TermOutline

final case class DefnMacro(
    mods: Mods,
    id: TermId,
    tparams: List[TypeParam],
    paramss: List[List[Param]],
    ret: Option[Tpt],
    rhs: Term)
    extends DefnDef
    with TermOutline

final case class DefnMethod(
    mods: Mods,
    id: TermId,
    tparams: List[TypeParam],
    paramss: List[List[Param]],
    ret: Option[Tpt],
    rhs: Option[Term])
    extends DefnDef
    with TermOutline

final case class DefnObject(
    mods: Mods,
    id: TermId,
    earlies: List[Stat],
    inits: List[Init],
    self: Option[Self],
    stats: List[Stat])
    extends DefnTemplate
    with TermOutline {
  def tparams = Nil
  def paramss = Nil
}

final case class DefnPackage(mods: Mods, pid: TermPath, stats: List[Stat])
    extends Stat
    with TermOutline {
  def id = pid.id.asInstanceOf[NamedId]
}

final case class DefnPackageObject(
    mods: Mods,
    id: TermId,
    earlies: List[Stat],
    inits: List[Init],
    self: Option[Self],
    stats: List[Stat])
    extends DefnTemplate
    with TermOutline {
  def tparams = Nil
  def paramss = Nil
}

final case class DefnPat(mods: Mods, pats: List[Pat], tpt: Option[Tpt], rhs: Option[Term])
    extends Stat

final case class DefnProcedure(
    mods: Mods,
    id: TermId,
    tparams: List[TypeParam],
    paramss: List[List[Param]],
    rhs: Option[Term])
    extends DefnDef
    with TermOutline {
  def ret = Some(TptId("Unit").withSym(UnitClass))
}

sealed trait DefnTemplate extends Stat with Parameterized with Outline {
  def mods: Mods
  def id: NamedId
  def earlies: List[Stat]
  def inits: List[Init]
  def self: Option[Self]
  def stats: List[Stat]
}

final case class DefnType(
    mods: Mods,
    id: TptId,
    tparams: List[TypeParam],
    lbound: Option[Tpt],
    ubound: Option[Tpt],
    rhs: Option[Tpt])
    extends Stat
    with Parameterized
    with TypeOutline {
  def lo = rhs.orElse(lbound).getOrElse(TptId("Nothing").withSym(NothingClass))
  def hi = rhs.orElse(ubound).getOrElse(TptId("Any").withSym(AnyClass))
  def paramss = Nil
}

sealed trait Enumerator extends Tree

final case class EnumeratorGenerator(pat: Pat, rhs: Term) extends Enumerator

final case class EnumeratorGuard(cond: Term) extends Enumerator

final case class EnumeratorVal(pat: Pat, rhs: Term) extends Enumerator

sealed trait Id extends Tree {
  var sym: Symbol = NoSymbol
  def withSym(sym: Symbol): this.type = {
    this.sym = sym
    this
  }
}

final case class Import(importers: List[Importer]) extends Stat

sealed trait Importee extends Tree

final case class ImporteeName(id: SomeId) extends Importee

final case class ImporteeRename(from: SomeId, to: SomeId) extends Importee

final case class ImporteeUnimport(id: SomeId) extends Importee

final case class ImporteeWildcard() extends Importee

final case class Importer(qual: TermPath, importees: List[Importee]) extends Tree

final case class Init(tpt: Tpt, argss: List[List[Term]]) extends Term {
  val id = CtorId()
}

sealed trait Mod extends Tree

sealed trait ModAccess extends Mod

final case class ModAbstract() extends Mod

final case class ModAnnotation(init: Init) extends Mod

final case class ModAnnotationInterface() extends Mod

final case class ModCase() extends Mod

final case class ModClass() extends Mod

final case class ModContravariant() extends Mod

final case class ModCovariant() extends Mod

final case class ModDefault() extends Mod

final case class ModEnum() extends Mod

final case class ModFinal() extends Mod

final case class ModImplicit() extends Mod

final case class ModInterface() extends Mod

final case class ModLazy() extends Mod

final case class ModNative() extends Mod

final case class ModOverride() extends Mod

final case class ModPrivate() extends ModAccess

final case class ModPrivateThis() extends ModAccess

final case class ModPrivateWithin(id: SomeId) extends ModAccess

final case class ModProtected() extends ModAccess

final case class ModProtectedThis() extends ModAccess

final case class ModProtectedWithin(id: SomeId) extends ModAccess

final case class ModPublic() extends ModAccess

final case class ModSealed() extends Mod

final case class ModStatic() extends Mod

final case class ModStrictfp() extends Mod

final case class ModSynchronized() extends Mod

final case class ModThrows(tpts: List[Tpt]) extends Mod

final case class ModTrait() extends Mod

final case class ModTransient() extends Mod

final case class ModVal() extends Mod

final case class ModVar() extends Mod

final case class ModVolatile() extends Mod

sealed trait Modded extends Tree {
  def mods: Mods
  def annots = mods.trees.collect { case x: ModAnnotation => x }
  def hasAbstract = mods.trees.exists(_.isInstanceOf[ModAbstract])
  def hasAnnotationInterface = mods.trees.exists(_.isInstanceOf[ModAnnotationInterface])
  def hasCase = mods.trees.exists(_.isInstanceOf[ModCase])
  def hasClass = mods.trees.exists(_.isInstanceOf[ModClass])
  def hasContravariant = mods.trees.exists(_.isInstanceOf[ModContravariant])
  def hasCovariant = mods.trees.exists(_.isInstanceOf[ModCovariant])
  def hasDefault = mods.trees.exists(_.isInstanceOf[ModDefault])
  def hasEnum = mods.trees.exists(_.isInstanceOf[ModEnum])
  def hasFinal = mods.trees.exists(_.isInstanceOf[ModFinal])
  def hasImplicit = mods.trees.exists(_.isInstanceOf[ModImplicit])
  def hasInterface = mods.trees.exists(_.isInstanceOf[ModInterface])
  def hasLazy = mods.trees.exists(_.isInstanceOf[ModLazy])
  def hasNative = mods.trees.exists(_.isInstanceOf[ModNative])
  def hasOverride = mods.trees.exists(_.isInstanceOf[ModOverride])
  def hasPrivate = mods.trees.exists(_.isInstanceOf[ModPrivate])
  def hasPrivateThis = mods.trees.exists(_.isInstanceOf[ModPrivateThis])
  def hasPrivateWithin = mods.trees.exists(_.isInstanceOf[ModPrivateWithin])
  def hasProtected = mods.trees.exists(_.isInstanceOf[ModProtected])
  def hasProtectedThis = mods.trees.exists(_.isInstanceOf[ModProtectedThis])
  def hasProtectedWithin = mods.trees.exists(_.isInstanceOf[ModProtectedWithin])
  def hasPublic = mods.trees.exists(_.isInstanceOf[ModPublic])
  def hasSealed = mods.trees.exists(_.isInstanceOf[ModSealed])
  def hasStatic = mods.trees.exists(_.isInstanceOf[ModStatic])
  def hasStrictfp = mods.trees.exists(_.isInstanceOf[ModStrictfp])
  def hasSynchronized = mods.trees.exists(_.isInstanceOf[ModSynchronized])
  def hasTrait = mods.trees.exists(_.isInstanceOf[ModTrait])
  def hasTransient = mods.trees.exists(_.isInstanceOf[ModTransient])
  def hasVal = mods.trees.exists(_.isInstanceOf[ModVal])
  def hasVar = mods.trees.exists(_.isInstanceOf[ModVar])
  def hasVolatile = mods.trees.exists(_.isInstanceOf[ModVolatile])
  def throws = mods.trees.collect { case x: ModThrows => x.tpts }.flatten
  def within = {
    mods.trees.collectFirst {
      case ModPrivateWithin(id) => id
      case ModProtectedWithin(id) => id
    }
  }
}

final case class Mods(trees: List[Mod]) extends Modded {
  def mods = this
}

sealed trait NamedId extends Id with Path {
  def id = this
  def value: String
  def name: Name
}

object NamedId {
  def unapply(id: NamedId): Some[String] = {
    Some(id.value)
  }
}

sealed trait Outline extends Modded {
  def id: Id
}

sealed trait Parameterized extends Outline {
  def paramss: List[List[Param]]
  def tparams: List[TypeParam]
}

final case class Param(mods: Mods, id: Id, tpt: Option[Tpt], rhs: Option[Term])
    extends Tree
    with TermOutline

sealed trait Pat extends Tree

final case class PatAlternative(pats: List[Pat]) extends Pat

final case class PatBind(pats: List[Pat]) extends Pat

final case class PatExtract(fun: TermPath, targs: List[Tpt], args: List[Pat]) extends Pat

final case class PatExtractInfix(lhs: Pat, op: TermId, rhs: Pat) extends Pat

final case class PatId(value: String) extends Pat with NamedId {
  def name = TermName(value)
}

final case class PatInterpolate(id: TermId, parts: List[PatLit], args: List[Pat]) extends Pat

final case class PatLit(value: Any) extends Pat

final case class PatRepeat(pat: Pat) extends Pat

final case class PatSelect(qual: TermPath, id: TermId) extends Pat

final case class PatTuple(args: List[Pat]) extends Pat

final case class PatVar(id: Id, tpt: Option[Tpt]) extends Pat with TermOutline {
  def mods = Mods(Nil)
}

// FIXME: https://github.com/twitter/rsc/issues/81
final case class PatXml(raw: String) extends Pat

sealed trait Path extends Tree {
  def id: Id
}

final case class PrimaryCtor(mods: Mods, paramss: List[List[Param]])
    extends DefnDef
    with TermOutline {
  val id = CtorId()
  def tparams = Nil
  def ret = Some(TptId("Unit").withSym(UnitClass))
}

final case class Self(id: Id, tpt: Option[Tpt]) extends Stat with TermOutline {
  def mods = Mods(Nil)
}

final case class SomeId(value: String) extends NamedId {
  def name = SomeName(value)
}

final case class Source(stats: List[Stat]) extends Tree

sealed trait Stat extends Tree

sealed trait Term extends Stat

final case class TermAnnotate(fun: Term, mods: Mods) extends Term

final case class TermApply(fun: Term, args: List[Term]) extends Term

final case class TermApplyInfix(lhs: Term, op: TermId, targs: List[Tpt], args: List[Term])
    extends Term

final case class TermApplyPostfix(arg: Term, op: TermId) extends Term

final case class TermApplyPrefix(op: TermId, arg: Term) extends Term

final case class TermApplyType(fun: Term, targs: List[Tpt]) extends Term

final case class TermAscribe(term: Term, tpt: Tpt) extends Term

final case class TermAssign(lhs: Term, rhs: Term) extends Term

final case class TermBlock(stats: List[Stat]) extends Term

final case class TermDo(body: Term, cond: Term) extends Term

final case class TermEta(term: Term) extends Term

final case class TermFor(enums: List[Enumerator], body: Term) extends Term

final case class TermForYield(enums: List[Enumerator], body: Term) extends Term

final case class TermFunction(params: List[Param], body: Term) extends Term

final case class TermId(value: String) extends TermPath with NamedId {
  def name = TermName(value)
}

final case class TermIf(cond: Term, thenp: Term, elsep: Option[Term]) extends Term

final case class TermInterpolate(id: TermId, parts: List[TermLit], args: List[Term]) extends Term

final case class TermLit(value: Any) extends Term

final case class TermMatch(term: Term, cases: List[Case]) extends Term

final case class TermNew(init: Init) extends Term

final case class TermNewAnonymous(
    earlies: List[Stat],
    inits: List[Init],
    self: Option[Self],
    stats: Option[List[Stat]]
) extends Term

sealed trait TermOutline extends Outline

final case class TermPartialFunction(cases: List[Case]) extends Term

sealed trait TermPath extends Term with Path

final case class TermRepeat(term: Term) extends Term

final case class TermReturn(term: Option[Term]) extends Term

final case class TermSelect(qual: Term, id: TermId) extends TermPath

final case class TermStub() extends Term

final case class TermSuper(qual: Id, mix: Id) extends TermPath {
  def id = mix
}

final case class TermThis(qual: Id) extends TermPath {
  def id = qual
}

final case class TermThrow(term: Term) extends Term

final case class TermTry(term: Term, catchp: List[Case], finallyp: Option[Term]) extends Term

final case class TermTryWithHandler(term: Term, catchp: Term, finallyp: Option[Term]) extends Term

final case class TermTuple(args: List[Term]) extends Term

final case class TermWhile(cond: Term, body: Term) extends Term

final case class TermWildcard() extends Term {
  val id = AnonId()
}

final case class TermWildcardFunction(ids: List[AnonId], body: Term) extends Term

// FIXME: https://github.com/twitter/rsc/issues/81
final case class TermXml(raw: String) extends Term

sealed trait Tpt extends Tree

final case class TptAnnotate(tpt: Tpt, mods: Mods) extends Tpt

final case class TptArray(tpt: Tpt) extends TptApply {
  def fun = TptId("Array").withSym(ArrayClass)
  def targs = List(tpt)
}

sealed trait TptApply extends Tpt {
  def fun: Tpt
  def targs: List[Tpt]
}

object TptApply {
  def unapply(tree: TptApply): Some[(Tpt, List[Tpt])] = {
    Some((tree.fun, tree.targs))
  }
}

final case class TptByName(tpt: Tpt) extends Tpt

final case class TptExistential(tpt: Tpt, stats: List[Stat]) extends Tpt

final case class TptFunction(targs: List[Tpt]) extends TptApply {
  def fun = {
    val value = "Function" + (targs.length - 1)
    TptId(value).withSym(FunctionClass(targs.length - 1))
  }
}

final case class TptId(value: String) extends TptPath with NamedId {
  def name = TypeName(value)
}

final case class TptIntersect(tpts: List[Tpt]) extends Tpt

sealed trait TptPath extends Tpt with Path

final case class TptParameterize(fun: Tpt, targs: List[Tpt]) extends TptApply

final case class TptParameterizeInfix(lhs: Tpt, op: TptId, rhs: Tpt) extends TptApply {
  def fun = op
  def targs = List(lhs, rhs)
}

final case class TptProject(qual: Tpt, id: TptId) extends TptPath

final case class TptRefine(tpt: Option[Tpt], stats: List[Stat]) extends Tpt

final case class TptRepeat(tpt: Tpt) extends Tpt

final case class TptSelect(qual: TermPath, id: TptId) extends TptPath

final case class TptSingleton(qual: TermPath) extends TptPath {
  def id = qual.id
}

final case class TptTuple(targs: List[Tpt]) extends TptApply {
  def fun = {
    val value = "Tuple" + targs.length
    TptId(value).withSym(TupleClass(targs.length))
  }
}

final case class TptWildcard(lbound: Option[Tpt], ubound: Option[Tpt]) extends Tpt {
  val id = AnonId()
  def lo = lbound.getOrElse(TptId("Nothing").withSym(NothingClass))
  def hi = ubound.getOrElse(TptId("Any").withSym(AnyClass))
}

final case class TptWildcardExistential(ids: List[AnonId], tpt: Tpt) extends Tpt

final case class TptWith(tpts: List[Tpt]) extends Tpt

sealed trait TypeOutline extends Outline

final case class TypeParam(
    mods: Mods,
    id: Id,
    tparams: List[TypeParam],
    lbound: Option[Tpt],
    ubound: Option[Tpt],
    vbounds: List[Tpt],
    cbounds: List[Tpt])
    extends Tree
    with Parameterized
    with TypeOutline {
  def lo = lbound.getOrElse(TptId("Nothing").withSym(NothingClass))
  def hi = ubound.getOrElse(TptId("Any").withSym(AnyClass))
  def paramss = Nil
}
