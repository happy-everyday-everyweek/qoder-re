// Ghidra headless decompile export
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class DecompileExport extends GhidraScript {

    @Override
    public void run() throws Exception {
        String outDir = System.getenv("GHIDRA_OUT");
        if (outDir == null || outDir.isEmpty()) {
            outDir = "/tmp/ghidra-out";
        }
        new File(outDir).mkdirs();

        int maxFuncs = 4000;
        String mf = System.getenv("GHIDRA_MAX_FUNCS");
        if (mf != null && !mf.isEmpty()) {
            try {
                maxFuncs = Integer.parseInt(mf);
            } catch (NumberFormatException e) {
                println("bad GHIDRA_MAX_FUNCS: " + mf);
            }
        }

        String name = currentProgram.getName();
        File out = new File(outDir, name + ".decompiled.c");
        PrintWriter w = new PrintWriter(new BufferedWriter(new FileWriter(out), 1 << 20));
        w.println("// decompiled by Ghidra headless :: " + name);
        w.println("// language: " + currentProgram.getLanguageID()
                + " compiler: " + currentProgram.getCompilerSpec().getCompilerSpecID()
                + " imageBase: " + currentProgram.getImageBase());

        DecompInterface di = new DecompInterface();
        di.toggleCCode(true);
        di.toggleSyntaxTree(true);
        di.openProgram(currentProgram);

        FunctionIterator it = currentProgram.getFunctionManager().getFunctions(true);
        int n = 0;
        int ok = 0;
        while (it.hasNext() && n < maxFuncs) {
            if (monitor.isCancelled()) {
                break;
            }
            Function fn = it.next();
            n++;
            w.println();
            w.println("// ==== FUNC " + n + " :: " + fn.getName()
                    + " @ " + fn.getEntryPoint()
                    + " (size=" + fn.getBody().getNumAddresses() + ")"
                    + (fn.isThunk() ? " [thunk]" : "")
                    + (fn.isExternal() ? " [external]" : "") + " ====");
            if (fn.isExternal() || fn.isThunk()) {
                continue;
            }
            try {
                DecompileResults res = di.decompileFunction(fn, 20, monitor);
                if (res != null && res.decompileCompleted()) {
                    w.println(res.getDecompiledFunction().getC());
                    ok++;
                } else {
                    w.println("// decompile incomplete");
                }
            } catch (Exception e) {
                w.println("// decompile exception: " + e);
            }
        }
        w.close();
        di.dispose();
        println("DecompileExport: functions=" + n + " decompiled=" + ok + " -> " + out.getAbsolutePath());
    }
}
