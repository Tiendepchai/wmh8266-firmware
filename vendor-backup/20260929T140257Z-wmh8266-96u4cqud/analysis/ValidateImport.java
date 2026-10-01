import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.listing.*;
import java.nio.file.*;
import java.io.*;
public class ValidateImport extends GhidraScript {
 public void run() throws Exception {
  String dir=getScriptArgs()[0];
  byte[] firmware=Files.readAllBytes(Path.of(dir).getParent().resolve("original.bin"));
  long[][] segs={{0x40201010L,0x1010,0x5df00},{0x40100000L,0x5ef20,0x7650},{0x3ffe8000L,0x66578,0xa84},{0x3ffe8a90L,0x67004,0x3060}};
  try(PrintWriter out=new PrintWriter(Path.of(dir,"import-validation.txt").toFile())){
   for(long[] s:segs){
    for(int i=0;i<s[2];i++)if(currentProgram.getMemory().getByte(toAddr(s[0]+i))!=firmware[(int)s[1]+i])throw new Exception("mapped bytes mismatch");
    MemoryBlock b=currentProgram.getMemory().getBlock(toAddr(s[0]));
    if(b.isExecute()!=(s[0]>=0x40100000L))throw new Exception("incorrect execute permission");
    out.printf("PASS flash 0x%x size 0x%x CPU 0x%x executable=%s all bytes identical%n",s[1],s[2],s[0],b.isExecute());
   }
   for(MemoryBlock b:currentProgram.getMemory().getBlocks())out.printf("BLOCK %s %s..%s initialized=%s read=%s write=%s execute=%s%n",b.getName(),b.getStart(),b.getEnd(),b.isInitialized(),b.isRead(),b.isWrite(),b.isExecute());
  }
  // Exploratory probes that landed inside later-recovered functions are not real entries.
  for(long a:new long[]{0x4022d4acL,0x4022e208L}){
   Function f=getFunctionAt(toAddr(a));if(f!=null && f.getName().startsWith("FUN_"))removeFunctionAt(toAddr(a));
  }
 }
}
