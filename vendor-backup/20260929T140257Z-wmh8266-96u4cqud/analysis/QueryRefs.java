import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
public class QueryRefs extends GhidraScript {
 public void run() throws Exception {
  String[] a=getScriptArgs();
  try(PrintWriter out=new PrintWriter(a[0])){
   for(int n=1;n<a.length;n++){
    Address target=toAddr(Long.parseLong(a[n].replace("0x",""),16));out.println("TARGET "+target);
    for(Reference r:getReferencesTo(target)){
     Function f=getFunctionContaining(r.getFromAddress());out.println(r.getFromAddress()+" "+r.getReferenceType()+" function="+(f==null?"none":f.getEntryPoint()));
     for(Reference r2:getReferencesTo(r.getFromAddress())){
      Function f2=getFunctionContaining(r2.getFromAddress());out.println("  VIA "+r2.getFromAddress()+" "+r2.getReferenceType()+" function="+(f2==null?"none":f2.getEntryPoint()));
     }
    }
   }
  }
 }
}
