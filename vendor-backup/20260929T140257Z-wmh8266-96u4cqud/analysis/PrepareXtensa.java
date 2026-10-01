// Offline mapped ESP8266 import setup. No connection to hardware.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;

public class PrepareXtensa extends GhidraScript {
 public void run() throws Exception {
  Memory mem=currentProgram.getMemory();
  for(MemoryBlock b:mem.getBlocks()) {
   long a=b.getStart().getOffset();
   b.setRead(true); b.setWrite(a<0x40000000L); b.setExecute(a>=0x40100000L && a<0x40300000L);
   b.setName(a>=0x40200000L?"IROM":a>=0x40100000L?"IRAM":"DRAM_"+Long.toHexString(a));
  }
  MemoryBlock bss=mem.createUninitializedBlock("RAM_uninitialized",toAddr(0x3ffebb00L),0x14500,false); bss.setWrite(true); bss.setExecute(false);
  MemoryBlock rom=mem.createUninitializedBlock("ROM_not_dumped",toAddr(0x40000000L),0x10000,false); rom.setWrite(false); rom.setExecute(true);
  MemoryBlock mmio=mem.createUninitializedBlock("MMIO",toAddr(0x60000000L),0x10000,false); mmio.setWrite(true); mmio.setExecute(false); mmio.setVolatile(true);
  addEntryPoint(toAddr(0x40100004L));
  disassemble(toAddr(0x40100004L));
  Function f=createFunction(toAddr(0x40100004L),"app_entry");
  if(f!=null)f.setCallingConvention("__call0");
  // Command dispatch handlers are reached indirectly. Seed only pointers whose
  // destination has the common call0 'addi a1,a1,-frame' prologue, then recurse.
  int seeded=0;
  for(MemoryBlock b:mem.getBlocks()) {
   if(!b.isInitialized())continue;
   for(long p=b.getStart().getOffset();p+4<=b.getEnd().getOffset()+1;p+=4) {
    long target=Integer.toUnsignedLong(mem.getInt(toAddr(p)));
    if(target<0x40100000L || target>=0x4025ef10L)continue;
    MemoryBlock dest=mem.getBlock(toAddr(target));
    if(dest==null || !dest.isInitialized() || !dest.isExecute())continue;
    if(mem.getByte(toAddr(target))==0x12 && (mem.getByte(toAddr(target+1))&255)==0xc1) {
     disassemble(toAddr(target));
     if(getFunctionAt(toAddr(target))==null && getFunctionContaining(toAddr(target))==null){
      Function fn=createFunction(toAddr(target),null); if(fn!=null){fn.setCallingConvention("__call0");seeded++;}
     }
    }
   }
  }
  println("Seeded indirect call0 functions: "+seeded);
 }
}
