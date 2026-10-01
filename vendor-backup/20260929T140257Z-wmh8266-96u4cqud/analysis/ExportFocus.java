import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class ExportFocus extends GhidraScript {
 public void run() throws Exception {
  String[] args=getScriptArgs();
  int count=0;
  for(Function f:currentProgram.getFunctionManager().getFunctions(true)){ f.setCallingConvention("__call0"); count++; }
  println("Functions call0: "+count);
  File dir=new File(args[0]);
  try(PrintWriter out=new PrintWriter(new File(dir,"function-index.tsv"))){
   for(Function f:currentProgram.getFunctionManager().getFunctions(true))out.println(f.getEntryPoint()+"\t"+f.getName()+"\t"+f.getBody().getNumAddresses());
  }
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(int ai=1;ai<args.length;ai++) {
   Address a=toAddr(Long.parseLong(args[ai].replace("0x",""),16));
   Function f=getFunctionAt(a);if(f==null)f=getFunctionContaining(a);
   if(f==null){disassemble(a);f=createFunction(a,null);if(f!=null)f.setCallingConvention("__call0");}
   String tag=a.toString();
   try(PrintWriter out=new PrintWriter(new File(dir,"focus-"+tag+".txt"))){
    out.println("REQUEST "+a);
    for(Reference r:getReferencesTo(a))out.println("XREF "+r.getFromAddress()+" "+r.getReferenceType());
    if(f==null){out.println("NO FUNCTION");continue;}
    out.println("FUNCTION "+f.getEntryPoint()+" "+f.getName()+" "+f.getCallingConventionName());
    for(Reference r:getReferencesTo(f.getEntryPoint()))out.println("CALLER "+r.getFromAddress()+" "+r.getReferenceType());
    DecompileResults res=d.decompileFunction(f,45,monitor);
    out.println(res.decompileCompleted()?res.getDecompiledFunction().getC():res.getErrorMessage());
    out.println("ASSEMBLY");
    for(Instruction ins:currentProgram.getListing().getInstructions(f.getBody(),true)){
     out.print(ins.getAddress()+"  "+ins);
     for(Reference r:ins.getReferencesFrom())out.print(" ; "+r.getReferenceType()+" "+r.getToAddress());
     out.println();
    }
   }
  }
  d.dispose();
 }
}
