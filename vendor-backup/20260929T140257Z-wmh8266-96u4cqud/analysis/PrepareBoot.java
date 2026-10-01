import ghidra.app.script.GhidraScript;
import ghidra.program.model.mem.*;
import ghidra.program.model.listing.*;
public class PrepareBoot extends GhidraScript {
 public void run() throws Exception {
  for(MemoryBlock b:currentProgram.getMemory().getBlocks()){
   boolean exec=b.getStart().getOffset()>=0x40100000L;
   b.setRead(true);b.setWrite(!exec);b.setExecute(exec);
  }
  addEntryPoint(toAddr(0x40100420L));disassemble(toAddr(0x40100420L));
  Function f=createFunction(toAddr(0x40100420L),"boot_entry");if(f!=null)f.setCallingConvention("__call0");
 }
}
