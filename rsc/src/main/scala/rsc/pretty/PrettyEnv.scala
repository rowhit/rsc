// Copyright (c) 2017-2018 Twitter, Inc.
// Licensed under the Apache License, Version 2.0 (see LICENSE.md).
package rsc.pretty

import rsc.outline._

object PrettyEnv {
  def str(p: Printer, x: Env): Unit = {
    p.str(x._scopes)
  }

  def repl(p: Printer, x: Env): Unit = {
    ???
  }
}
