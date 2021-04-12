/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.io.BaseOutputReader
import org.rust.cargo.runconfig.RsProcessHandler

class RsWslProcessHandler(
    commandLine: GeneralCommandLine,
    processColors: Boolean
) : RsProcessHandler(commandLine, processColors) {
    override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.BLOCKING
}
