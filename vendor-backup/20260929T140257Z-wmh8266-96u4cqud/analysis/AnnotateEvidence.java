import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.data.*;

public class AnnotateEvidence extends GhidraScript {
 void name(long addr,String n,String comment) throws Exception {
  Address a=toAddr(addr); Function f=getFunctionAt(a);
  if(f==null){disassemble(a);f=createFunction(a,n);}
  if(f!=null){f.setName(n,SourceType.USER_DEFINED);f.setCallingConvention("__call0");f.setComment(comment);}
  setPlateComment(a,comment);
 }
 void signature(long addr,DataType ret,DataType... params) throws Exception {
  Function f=getFunctionAt(toAddr(addr));
  Parameter[] p=new Parameter[params.length];
  for(int i=0;i<p.length;i++)p[i]=new ParameterImpl("arg"+i,params[i],currentProgram);
  f.updateFunction("__call0",new ReturnParameterImpl(ret,currentProgram),Function.FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS,true,SourceType.USER_DEFINED,p);
 }
 public void run() throws Exception {
  name(0x4022d4a8L,"at_gmr_execute","Command table +GMR at flash 0x5c458. AT version arguments 1,1,0,0; build May 11 2016 18:09:56.");
  name(0x4020525cL,"sdk_version_string","Returns 1.5.4(baaeaebb); identified by +GMR caller.");
  name(0x4022d7ecL,"at_builtin_command_lookup","0x18-byte records, exact command length and ROM comparison; not an HTTP form parser.");
  name(0x4022d914L,"at_command_dispatch","Command suffix CR / ?CR / =?CR / =value selects execute/query/test/set function pointers.");
  name(0x4022da48L,"at_parse_integer","AT parameter integer parser; not web form decoding.");
  name(0x40232318L,"at_uart_receive_task","Reads UART input; assembles AT commands or exact-length CIPSEND data according to state.");
  name(0x40232950L,"at_process_pending","State 2 dispatches AT command; state 4 sends supplied payload; state 5 transparent data path.");
  name(0x402329b0L,"at_stack_init","Registers UART receive/pending task callbacks and loads AT configuration.");
  name(0x402308ecL,"at_cipserver_set","Dispatch record at flash 0x5c938. Creates TCP server and registers accept callback 0x40230794; uses configurable timeout at 0x3ffe8378.");
  name(0x40230794L,"at_tcp_accept_callback","Receive callback installed at 0x40230850 is 0x4022e004; close/sent callbacks also registered.");
  name(0x4022e004L,"at_tcp_receive_forward","TCP payload copied unchanged after +IPD header into UART output. No RLOF/RLON comparisons or relay action here.");
  name(0x402358c0L,"at_output_cstring","ROM strlen then output(port/link sentinel -1,buffer,length,enqueue=1).");
  name(0x40235d40L,"at_output_bytes","Forwards arguments to UART queue unless optional output hook configured. Assembly preserves a2-a5 for UART call.");
  name(0x40235c28L,"uart_output_queue","Queues buffer, enables UART TX interrupt; forwards unchanged payload bytes.");
  name(0x40106490L,"uart_ring_append","Appends bytes to transmit ring buffer, handling wrap.");
  name(0x40106698L,"uart_fifo_drain","Byte writes to 0x60000000 + uart_index*0xf00. This is UART FIFO, not relay GPIO.");
  name(0x40106710L,"uart_tx_service","Drains output ring through uart_fifo_drain.");
  name(0x4022fae4L,"at_cipsend_set","Validates link and payload length, switches UART parser to raw-data state; emits prompt.");
  name(0x4022fef4L,"at_submit_tx_payload","Sends exact-length UART-supplied buffer via link send callback (0x4022692c for TCP). No HTML generation established.");
  name(0x4022e4fcL,"at_tcp_sent_callback","Outputs SEND OK and resets send state; does not directly close connection.");
  name(0x4022f640L,"at_cipclose_set","Selects link and invokes close function at link+0x38 (0x40226d44 for TCP).");
  name(0x4022f95cL,"at_cipclose_execute","Single-link no-argument CIPCLOSE path.");
  name(0x40230f70L,"at_cipsto_set","Accepts range 0..7200, writes runtime timeout at 0x3ffe8378, then calls 0x40226d04. Initial flash value is 180, not 3.");
  name(0x40230f34L,"at_cipsto_query","Reports runtime timeout; not queried on hardware.");
  name(0x40226d04L,"espconn_set_idle_timeout","Inferred API role: stores requested value into matching espconn internal node +0x28. No time unit inferred solely from this store.");
  name(0x40220dd8L,"tcp_server_idle_poll","Increments counter at node+0x2c; compares node/server timeout at +0x28; expires via close path. Poll cadence requires separate lwIP timing analysis.");
  name(0x40226d44L,"espconn_disconnect_inferred","Explicit TCP close path selected by CIPCLOSE; reaches 0x40220750/0x40220774.");
  name(0x40230564L,"at_tcp_closed_callback","Outputs CLOSED notification to serial host.");
  name(0x402455a0L,"extension_gpio_valid","Pin mask 0x1f03f: 0..5 and 12..16. Does not identify a relay pin.");
  name(0x402459d0L,"at_ciowrite_set","Extension table at flash 0x66e18; parses requested pin and level, calls generic GPIO writer.");
  name(0x402455fcL,"extension_gpio_write","Generic requested-pin output mux and ROM GPIO setter; level=1 drives high, 0 low. Electrical relay polarity unknown.");
  name(0x4024597cL,"at_ciomode_set","Command parsed and acknowledged; inspected instructions do not call a GPIO mode setter.");
  name(0x40245a40L,"at_cioread_set","Reads requested GPIO; no relay-state mapping established.");
  DataType u=UnsignedIntegerDataType.dataType, v=VoidDataType.dataType, ptr=new PointerDataType(ByteDataType.dataType);
  signature(0x40235d40L,v,u,ptr,u,u); signature(0x40235c28L,v,u,ptr,u,u);
  signature(0x4022e004L,v,ptr,ptr,u);signature(0x402358c0L,v,ptr);
  signature(0x402455fcL,u,u,u);signature(0x40226d04L,u,ptr,u,u);
  name(0x400018b4L,"rom_memcpy_inferred","ROM bytes absent; role inferred from repeated three-argument copy calls.");signature(0x400018b4L,ptr,ptr,ptr,u);
  name(0x40002ac8L,"rom_strlen_inferred","ROM bytes absent; string length role inferred from callers.");signature(0x40002ac8L,u,ptr);
  name(0x40002ae8L,"rom_bzero_inferred","ROM bytes absent; zero-buffer role inferred from callers.");signature(0x40002ae8L,v,ptr,u);
  name(0x40004cd0L,"rom_gpio_output_set_inferred","ROM bytes absent. Caller provides set,clear,enable,disable bitmasks; not a known relay pin.");signature(0x40004cd0L,v,u,u,u,u);
  createLabel(toAddr(0x3ffe8378L),"at_server_timeout_default_180",true);
  setPlateComment(toAddr(0x3ffe8378L),"Flash 0x668f0 initialized value 0xb4. Mutable runtime AT+CIPSTO; no runtime RAM snapshot available.");
 }
}
