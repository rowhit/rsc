// Copyright (c) 2017-2018 Twitter, Inc.
// Licensed under the Apache License, Version 2.0 (see LICENSE.md).
package rsc.outline

import java.util.LinkedHashSet
import rsc.gensym._
import rsc.input._
import rsc.report._
import rsc.semantics._
import rsc.settings._
import rsc.syntax._
import rsc.util._

// FIXME: https://github.com/twitter/rsc/issues/98
final class Scheduler private (
    settings: Settings,
    reporter: Reporter,
    gensyms: Gensyms,
    symtab: Symtab,
    todo: Todo) {
  private lazy val synthesizer: Synthesizer = {
    Synthesizer(settings, reporter, gensyms, symtab, todo)
  }

  def apply(env: Env, tree: Tree): Env = {
    tree match {
      case tree: DefnConstant => defnConstant(env, tree)
      case tree: DefnDef => defnDef(env, tree)
      case tree: DefnField => defnField(env, tree)
      case tree: DefnPackage => defnPackage(env, tree)
      case tree: DefnPat => defnPat(env, tree)
      case tree: DefnTemplate => defnTemplate(env, tree)
      case tree: DefnType => defnType(env, tree)
      case tree: Param => param(env, tree)
      case tree: Source => source(env, tree)
      case tree: TypeParam => tparam(env, tree)
      case _ => crash(tree)
    }
  }

  private def assignSym(env: Env, outline: Outline): Unit = {
    val scope = {
      outline match {
        case DefnPackage(_, TermSelect(qual: TermPath, _), _) =>
          val parentPackage = DefnPackage(Mods(Nil), qual, Nil).withPos(outline.pos)
          apply(env, parentPackage)
          symtab.scopes(qual.id.sym)
        case DefnPackageObject(_, id, _, _, _, _) =>
          val packageSym = PackageSymbol(env.owner.sym, id.value)
          env.owner.enter(id.name, packageSym)
          val packageScope = {
            val existingScope = symtab._scopes.get(packageSym)
            if (existingScope != null) {
              existingScope
            } else {
              val rootScope = symtab.scopes(RootPackage).asInstanceOf[PackageScope]
              val newScope = PackageScope(packageSym, rootScope._index)
              symtab.scopes(packageSym) = newScope
              todo.add(env, newScope)
              newScope
            }
          }
          packageScope
        case _ =>
          env.owner
      }
    }
    outline match {
      case outline: DefnField =>
        outline.lang match {
          case ScalaLanguage | UnknownLanguage =>
            // FIXME: https://github.com/twitter/rsc/issues/93
            val getter = DefnMethod(
              outline.mods.dupe,
              TermId(outline.id.value).withPos(outline.id.pos),
              Nil,
              Nil,
              outline.tpt.map(_.dupe),
              outline.rhs.map(_.dupe)).withPos(outline.pos)
            apply(env, getter)
            if (outline.hasVar) {
              val param =
                Param(Mods(Nil), TermId("x$1"), outline.tpt.map(_.dupe), None).withPos(outline.pos)
              val setter = DefnMethod(
                // FIXME: https://github.com/twitter/rsc/issues/93
                outline.mods.filter(!_.isInstanceOf[ModImplicit]),
                TermId(outline.id.value + "_=").withPos(outline.id.pos),
                Nil,
                List(List(param)),
                Some(TptId("Unit").withSym(UnitClass)),
                outline.rhs.map(_.dupe)
              ).withPos(outline.pos)
              apply(env, setter)
            }
            if (outline.hasBean) {
              // FIXME: https://github.com/twitter/rsc/issues/293
              val beanGetter = DefnMethod(
                outline.mods.filter(!_.isInstanceOf[ModVal]),
                TermId("get" + outline.id.value.capitalize).withPos(outline.id.pos),
                Nil,
                List(List()),
                outline.tpt.map(_.dupe),
                outline.rhs.map(_.dupe)
              ).withPos(outline.pos)
              apply(env, beanGetter)
            }
          case JavaLanguage =>
            val sym = TermSymbol(scope.sym, outline.id.value)
            scope.enter(outline.id.name, sym)
            outline.id.sym = sym
            symtab._outlines.put(sym, outline)
            symtab._envs.put(sym, env)
        }
      case outline =>
        val sym = {
          if (scope.sym.isGlobal) {
            outline match {
              case outline: DefnClass =>
                TypeSymbol(scope.sym, outline.id.value)
              case outline: DefnConstant =>
                TermSymbol(scope.sym, outline.id.value)
              case outline: DefnDef =>
                if (outline.hasVal) {
                  TermSymbol(scope.sym, outline.id.value)
                } else {
                  def loop(attempt: Int): String = {
                    val disambig = if (attempt == 0) s"()" else s"(+$attempt)"
                    val sym = MethodSymbol(scope.sym, outline.id.value, disambig)
                    if (symtab._outlines.containsKey(sym)) loop(attempt + 1)
                    else sym
                  }
                  loop(0)
                }
              case outline: DefnField =>
                crash(outline)
              case outline: DefnObject =>
                TermSymbol(scope.sym, outline.id.value)
              case outline: DefnPackage =>
                PackageSymbol(scope.sym, outline.id.value)
              case outline: DefnPackageObject =>
                TermSymbol(scope.sym, "package")
              case outline: DefnType =>
                TypeSymbol(scope.sym, outline.id.value)
              case outline: Param =>
                val gensym = gensyms(outline)
                outline.id match {
                  case AnonId() => ParamSymbol(scope.sym, gensym.anon())
                  case id: NamedId => ParamSymbol(scope.sym, id.value)
                }
              case outline: PatVar =>
                val gensym = gensyms(outline)
                outline.id match {
                  case AnonId() => TermSymbol(scope.sym, gensym.anon())
                  case id: NamedId => TermSymbol(scope.sym, id.value)
                }
              case outline: Self =>
                SelfSymbol(scope.sym)
              case outline: TypeParam =>
                outline.id match {
                  case AnonId() => TypeParamSymbol(scope.sym, "_")
                  case id: NamedId => TypeParamSymbol(scope.sym, id.value)
                }
            }
          } else {
            val gensym = gensyms.global
            LocalSymbol(gensym)
          }
        }
        outline.id match {
          case id: NamedId =>
            outline match {
              case _: DefnPackageObject => scope.enter(TermName("package"), sym)
              case _ => scope.enter(id.name, sym)
            }
          case id: AnonId =>
            ()
        }
        outline.id.sym = sym
        symtab._outlines.put(sym, outline)
        symtab._envs.put(sym, env)
        if (outline.hasStatic) {
          symtab._statics.add(sym.owner)
          symtab._statics.add(sym.owner.companionObject)
        }
    }
  }

  private def defnConstant(env: Env, tree: DefnConstant): Env = {
    mods(env, tree.mods)
    assignSym(env, tree)
    env
  }

  private def defnDef(env: Env, tree: DefnDef): Env = {
    // NOTE: Primary ctors are typechecked in env.outer,
    // but their mods are typechecked in env.
    // This is inconsistent, and unfortunately not mentioned in SLS.
    mods(env, tree.mods)
    assignSym(env, tree)
    val defEnv = if (tree.isInstanceOf[PrimaryCtor]) env.outer else env
    val tparamEnv = tparams(defEnv, tree)
    synthesizer.paramss(env, tree)
    val paramEnv = paramss(tparamEnv, tree)
    tree.ret match {
      case Some(ret) =>
        todo.add(paramEnv, ret)
      case None =>
        def infer(tpt: Tpt): Unit = {
          symtab._inferred.put(tree.id.sym, tpt)
          todo.add(paramEnv, tpt)
        }
        tree match {
          case DefnMethod(mods, _, _, _, _, Some(rhs)) if mods.hasImplicit =>
            rhs match {
              case TermApplyType(TermId("implicitly"), List(tpt)) =>
                infer(tpt)
              case TermApplyType(TermSelect(TermId("Bijection"), TermId("connect")), tpts) =>
                val bijection = TptId("Bijection").withSym(BijectionClass)
                infer(TptParameterize(bijection, tpts))
              case _ =>
                ()
            }
          case _ =>
            ()
        }
    }
    env
  }

  private def defnField(env: Env, tree: DefnField): Env = {
    mods(env, tree.mods)
    assignSym(env, tree)
    tree.tpt.foreach(todo.add(env, _))
    env
  }

  private def defnPackage(env: Env, tree: DefnPackage): Env = {
    assignSym(env, tree)
    val packageScope = {
      val existingScope = symtab._scopes.get(tree.id.sym)
      if (existingScope != null) {
        existingScope
      } else {
        val rootScope = symtab.scopes(RootPackage).asInstanceOf[PackageScope]
        val newScope = PackageScope(tree.id.sym, rootScope._index)
        symtab.scopes(tree.id.sym) = newScope
        todo.add(env, newScope)
        newScope
      }
    }
    val packageEnv = packageScope :: env
    stats(PackageLevel, packageEnv, tree.stats)
    env
  }

  private def defnPat(env: Env, tree: DefnPat): Env = {
    def loop(env: Env, pat: Pat): Unit = {
      pat match {
        case PatAlternative(pats) =>
          pats.foreach(loop(env, _))
        case PatBind(pats) =>
          pats.foreach(loop(env, _))
        case PatExtract(_, _, args) =>
          args.foreach(loop(env, _))
        case PatExtractInfix(lhs, _, rhs) =>
          loop(env, lhs)
          loop(env, rhs)
        case PatId(_) =>
          ()
        case PatInterpolate(_, _, args) =>
          args.foreach(loop(env, _))
        case PatLit(_) =>
          ()
        case PatRepeat(pat) =>
          loop(env, pat)
        case PatSelect(_, _) =>
          ()
        case PatTuple(args) =>
          args.foreach(loop(env, _))
        case pat @ PatVar(mods, id, tpt) =>
          id match {
            case id: TermId =>
              val fieldMods = Mods(tree.mods.trees ++ mods.trees)
              val fieldTpt = tpt.orElse(tree.tpt)
              val fieldRhs = Some(TermStub())
              val field = DefnField(fieldMods, id, fieldTpt, fieldRhs)
              apply(env, field.withPos(tree.pos))
            case _ =>
              ()
          }
        case PatXml(_) =>
          ()
      }
    }
    mods(env, tree.mods)
    tree.pats.foreach(loop(env, _))
    tree.tpt.foreach(todo.add(env, _))
    env
  }

  private def defnTemplate(env: Env, tree: DefnTemplate): Env = {
    mods(env, tree.mods)
    assignSym(env, tree)
    val tparamEnv = tparams(env, tree)
    val selfEnv = self(tparamEnv, tree)
    val templateEnv = {
      tree match {
        case tree: DefnPackageObject =>
          val packageScope = symtab.scopes(tree.id.sym.owner).asInstanceOf[PackageScope]
          val templateScope = PackageObjectScope(tree, packageScope)
          val templateEnv = templateScope :: packageScope :: selfEnv
          symtab.scopes(tree.id.sym) = templateScope
          todo.add(packageScope :: tparamEnv, templateScope)
          templateEnv
        case tree =>
          val templateScope = TemplateScope(tree)
          val templateEnv = templateScope :: selfEnv
          symtab.scopes(tree.id.sym) = templateScope
          todo.add(tparamEnv, templateScope)
          templateEnv
      }
    }
    stats(TemplateLevel, templateEnv, tree.earlies)
    tree match {
      case tree: DefnClass =>
        tree.lang match {
          case ScalaLanguage | UnknownLanguage =>
            tree.primaryCtor.foreach(synthesizer.paramss(templateEnv, _))
            synthesizer.paramAccessors(templateEnv, tree)
            tree.primaryCtor.foreach(apply(templateEnv, _))
          case JavaLanguage =>
            val hasCtor = tree.stats.exists(_.isInstanceOf[DefnCtor])
            if (!hasCtor && tree.hasClass) synthesizer.defaultConstructor(templateEnv, tree)
        }
      case tree: DefnObject =>
        val companionClass = symtab._outlines.get(tree.id.sym.companionClass)
        companionClass match {
          case caseClass: DefnClass if caseClass.hasDefaultParams =>
            synthesizer.defaultGetters(templateEnv, caseClass)
          case _ =>
            ()
        }
      case _ =>
        ()
    }
    stats(TemplateLevel, templateEnv, tree.stats)
    tree match {
      case tree: DefnClass =>
        if (tree.hasCase) {
          synthesizer.caseClassMembers(templateEnv, tree)
        }
        if (tree.hasEnum) {
          synthesizer.enumMembers(templateEnv, tree)
        }
        tree.parents.foreach {
          case Init(TptId("AnyVal"), Nil) =>
            synthesizer.valueClassMembers(templateEnv, tree)
          case _ =>
            ()
        }
      case tree: DefnObject =>
        if (tree.hasCase) {
          synthesizer.caseObjectMembers(templateEnv, tree)
        }
        val companionClass = symtab._outlines.get(tree.id.sym.companionClass)
        companionClass match {
          case caseClass: DefnClass if caseClass.hasCase =>
            synthesizer.caseClassCompanionMembers(templateEnv, caseClass)
          case _ =>
            ()
        }
      case _ =>
        ()
    }
    env
  }

  private def defnType(env: Env, tree: DefnType): Env = {
    mods(env, tree.mods)
    assignSym(env, tree)
    val tparamEnv = tparams(env, tree)
    tree.lo.foreach(todo.add(tparamEnv, _))
    tree.hi.foreach(todo.add(tparamEnv, _))
    tree.rhs.foreach(todo.add(tparamEnv, _))
    env
  }

  private def mods(env: Env, mods: Mods): Env = {
    mods.annots.foreach(annot => todo.add(env, annot.init.tpt))
    mods.within.foreach(todo.add(env, _))
    env
  }

  private def param(env: Env, tree: Param): Env = {
    // NOTE: Parameter types are typechecked in env.outer,
    // but their mods are typechecked in env.
    // This is inconsistent, and unfortunately not mentioned in SLS.
    mods(env, tree.mods)
    assignSym(env, tree)
    tree.tpt.foreach(todo.add(env.outer, _))
    env
  }

  private def paramss(env: Env, owner: Parameterized): Env = {
    val paramss = symtab._paramss.get(owner)
    paramss.foldLeft(env) { (env, params) =>
      if (params.nonEmpty) {
        val paramScope = ParamScope(owner.id.sym)
        val paramEnv = paramScope :: env
        params.foreach(apply(paramEnv, _))
        paramScope.succeed()
        paramEnv
      } else {
        env
      }
    }
  }

  private def self(env: Env, owner: DefnTemplate): Env = {
    owner.self match {
      case Some(tree) =>
        val selfScope = SelfScope(owner.id.sym)
        val selfEnv = selfScope :: env
        assignSym(selfEnv, tree)
        tree.tpt match {
          case Some(tpt) =>
            todo.add(selfEnv, tpt)
          case None =>
            val inferredTpt = {
              val ownerRef = owner.id match {
                case id: TermId => TptSingleton(id)
                case id: TptId => id
                case other => crash(other)
              }
              val tparamRefs = owner.tparams.map(_.id).map {
                case id: TptId => id
                case other => crash(other)
              }
              if (owner.tparams.isEmpty) ownerRef
              TptParameterize(ownerRef, tparamRefs)
            }
            symtab._inferred.put(tree.id.sym, inferredTpt)
            todo.add(selfEnv, inferredTpt)
        }
        selfScope.succeed()
        selfEnv
      case None =>
        env
    }
  }

  private def source(env: Env, tree: Source): Env = {
    def wildcardImport(qual: Path, env: Env): Env = {
      val importer = Importer(Mods(Nil), qual, List(ImporteeWildcard()))
      val scope = ImporterScope(importer)
      todo.add(env, scope)
      scope :: env
    }
    val sourceEnv = tree.lang match {
      case ScalaLanguage | UnknownLanguage =>
        val rootEnv = symtab.scopes(RootPackage) :: env
        val javaLangEnv = wildcardImport(TermSelect(TermId("java"), TermId("lang")), rootEnv)
        val scalaEnv = wildcardImport(TermId("scala"), javaLangEnv)
        val needsPredef = tree.toplevelImporters.forall {
          case Importer(_, TermSelect(TermId("scala"), TermId("Predef")), _) => false
          case _ => true
        }
        if (needsPredef) wildcardImport(TermSelect(TermId("scala"), TermId("Predef")), scalaEnv)
        else scalaEnv
      case JavaLanguage =>
        val rootEnv = symtab.scopes(RootPackage) :: env
        wildcardImport(TermSelect(TermId("java"), TermId("lang")), rootEnv)
    }
    stats(SourceLevel, sourceEnv, tree.stats)
    env
  }

  private sealed trait Level
  private case object SourceLevel extends Level
  private case object PackageLevel extends Level
  private case object TemplateLevel extends Level

  private def stats(level: Level, env: Env, trees: List[Stat]): Env = {
    var essentialObjects: LinkedHashSet[Symbol] = null
    trees.foreach {
      case outline: DefnClass if outline.hasCase || outline.hasDefaultParams =>
        if (essentialObjects == null) {
          essentialObjects = new LinkedHashSet[Symbol]
        }
        val ownerSym = if (level == SourceLevel) EmptyPackage else env.owner.sym
        val classSym = TypeSymbol(ownerSym, outline.id.value)
        outline.id.sym = classSym
        symtab._outlines.put(classSym, outline)
        essentialObjects.add(classSym.companionObject)
      case _ =>
        ()
    }

    var currEnv = env
    def outlineEnv(currEnv: Env, tree: Outline): Env = {
      tree match {
        case _: DefnPackage | _: DefnPackageObject => currEnv
        case _ if level == SourceLevel => symtab.scopes(EmptyPackage) :: currEnv
        case _ => currEnv
      }
    }
    trees.foreach {
      case tree: Import =>
        tree.importers.foreach { importer =>
          val scope = ImporterScope(importer)
          todo.add(currEnv, scope)
          currEnv = scope :: currEnv
        }
      case tree: Outline =>
        val treeEnv = outlineEnv(currEnv, tree)
        apply(treeEnv, tree)
        tree match {
          case tree: DefnClass if tree.hasImplicit =>
            synthesizer.implicitClassConversion(currEnv, tree)
          case _: DefnCtor | _: PrimaryCtor =>
            ()
          case tree: DefnDef if tree.hasDefaultParams =>
            synthesizer.defaultGetters(treeEnv, tree)
          case _ =>
            ()
        }
      case tree: DefnPat =>
        apply(currEnv, tree)
      case _ =>
        ()
    }

    if (essentialObjects != null) {
      val essentialObjectsIt = essentialObjects.iterator
      while (essentialObjectsIt.hasNext) {
        val objectSym = essentialObjectsIt.next()
        val needsSynthesis = !symtab._scopes.containsKey(objectSym)
        if (needsSynthesis) {
          val classSym = objectSym.companionClass
          val classTree = symtab._outlines.get(classSym).asInstanceOf[DefnClass]
          val env = outlineEnv(currEnv, classTree)
          synthesizer.syntheticCompanion(env, classTree)
        }
      }
    }
    currEnv
  }

  private def tparam(env: Env, tree: TypeParam): Env = {
    mods(env, tree.mods)
    assignSym(env, tree)
    val tparamEnv = tparams(env, tree)
    tree.ubound.foreach(todo.add(tparamEnv, _))
    tree.lbound.foreach(todo.add(tparamEnv, _))
    tree.vbounds.foreach(todo.add(tparamEnv, _))
    tree.cbounds.foreach(todo.add(tparamEnv, _))
    env
  }

  private def tparams(env: Env, owner: Parameterized): Env = {
    if (owner.tparams.nonEmpty) {
      val tparamScope = TypeParamScope(owner.id.sym)
      val tparamEnv = tparamScope :: env
      owner.tparams.foreach(apply(tparamEnv, _))
      tparamScope.succeed()
      tparamEnv
    } else {
      env
    }
  }

  private implicit class ClassDefaultOps(tree: DefnClass) {
    def hasDefaultParams: Boolean = {
      def primaryHasDefaultParams = {
        tree.primaryCtor.map(_.hasDefaultParams).getOrElse(false)
      }
      def secondaryHasDefaultParams = tree.stats.exists {
        case stat: DefnCtor => stat.hasDefaultParams
        case _ => false
      }
      primaryHasDefaultParams || secondaryHasDefaultParams
    }
  }

  private implicit class DefDefaultOps(tree: DefnDef) {
    def hasDefaultParams: Boolean = {
      tree.paramss.flatten.exists(_.rhs.nonEmpty)
    }
  }
}

object Scheduler {
  def apply(
      settings: Settings,
      reporter: Reporter,
      gensyms: Gensyms,
      symtab: Symtab,
      todo: Todo): Scheduler = {
    new Scheduler(settings, reporter, gensyms, symtab, todo)
  }
}
