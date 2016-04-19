package edu.asu.plp.tool.backend.plpisa.sim.stages;

import com.google.common.eventbus.EventBus;

import edu.asu.plp.tool.backend.plpisa.InstructionExtractor;
import edu.asu.plp.tool.backend.plpisa.sim.ALU;
import edu.asu.plp.tool.backend.plpisa.sim.SimulatorFlag;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.ExecuteCompletion;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.ExecuteStageStateRequest;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.ExecuteStageStateResponse;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.InstructionDecodeCompletion;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.MemoryStageStateRequest;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.MemoryStageStateResponse;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.WriteBackStageStateRequest;
import edu.asu.plp.tool.backend.plpisa.sim.stages.events.WriteBackStageStateResponse;
import edu.asu.plp.tool.backend.plpisa.sim.stages.state.CpuState;

public class ExecuteStage implements Stage
{
	private EventBus bus;
	private ExecuteEventHandler eventHandler;
	
	private CpuState state;
	private CpuState currentMemoryStageState;
	private CpuState currentWriteBackStageState;
	
	private ALU alu;
	
	public ExecuteStage(EventBus simulatorBus)
	{
		this.bus = simulatorBus;
		this.eventHandler = new ExecuteEventHandler();
		
		this.bus.register(eventHandler);
		
		this.state = new CpuState();
		
		alu = new ALU();
		
		reset();
	}
	
	@Override
	public void evaluate()
	{
		//@formatter:off
		ExecuteCompletion memoryPackage = new ExecuteCompletion();
		CpuState postMemoryStageState = new CpuState();
		
		memoryPackage.setPostMemoryStageState(postMemoryStageState);
		
		currentMemoryStageState = null;
		currentWriteBackStageState = null;
		
		bus.post(new MemoryStageStateRequest());
		bus.post(new WriteBackStageStateRequest());
		
		if(currentMemoryStageState == null)
			throw new IllegalStateException("Could not retrieve memory stage state.");
		if(currentWriteBackStageState == null)
			throw new IllegalStateException("Could not retrieve write back stage state.");
		
		boolean writeBackCt1Regwrite = (currentWriteBackStageState.ct1Regwrite == 1);
        boolean memCt1Regwrite = (currentMemoryStageState.forwardCt1Regwrite == 1);
        
        long executeRs = InstructionExtractor.rs(state.currentInstruction);
        long executeRt = InstructionExtractor.rt(state.currentInstruction);
		
		if(state.hot)
		{
			state.hot = false;
			postMemoryStageState.hot = true;
		}
		
		if(!state.bubble)
			state.count++;
		
		postMemoryStageState.nextBubble = state.bubble;
		postMemoryStageState.nextInstruction = state.currentInstruction;
		postMemoryStageState.nextInstructionAddress = state.currentInstructionAddress;
		
		//TODO Simulation flag stuff
		//Forward logic for rs source, 1 for EX->EX, 2 for MEM->EX
		boolean exEx = false; //ex_ex && memCt1Regwrite && currentMemoryStageState.forwardCt1DestRegAddress == executeRs && executeRs != 0
		boolean memEx = false; //mem_ex && writeBackCt1Regwrite && currentWriteBackStageState.ct1DestRegAddress == executeRs && executeRs != 0
		
		state.ct1Forwardx = exEx ? 1 : memEx ? 2 : 0;
		
		if(state.ct1Forwardx == 1)
		{
			//simFlags.add(SimulatorFlag.PLP_SIM_FWD_EX_EX_RS);
		}
		else if(state.ct1Forwardx == 2)
		{
			//simFlags.add(SimulatorFlag.PLP_SIM_FWD_MEM_EX_RS);
		}
		
		//Foward logic for rt source, 1 for EX->EX, 2 for MEM->EX
		exEx = false; //ex_ex && memCt1Regwrite && currentMemoryStageState.forwardCt1DestRegAddress == executeRt && executeRt != 0
		memEx = false; //mem_ex && writeBackCt1Regwrite && currentWriteBackStageState.ct1DestRegAddress == executeRt && executeRt != 0
		
		state.ct1Forwardy = exEx ? 1 : memEx ? 2 : 0;
		
		if(state.ct1Forwardy == 1)
		{
			//simFlags.add(SimulatorFlag.PLP_SIM_FWD_EX_EX_RT);
		}
		else if(state.ct1Forwardy == 2)
		{
			//simFlags.add(SimulatorFlag.PLP_SIM_FWD_MEM_EX_RT);
		}
		
		//Cant switch on longs. 
		state.dataX = (state.ct1Forwardx == 0) ? state.dataRs :
            (state.ct1Forwardx == 1) ? currentMemoryStageState.forwardDataAluResult :
            (state.ct1Forwardx == 2) ? currentWriteBackStageState.dataRegwrite : 0;
		
		state.dataEffY = (state.ct1Forwardy == 0) ? state.dataRt :
            (state.ct1Forwardy == 1) ? currentMemoryStageState.forwardDataAluResult :
            (state.ct1Forwardy == 2) ? currentWriteBackStageState.dataRegwrite : 0;
		
		state.dataY = (state.ct1Alusrc == 1) ? state.dataImmediateSignextended : state.dataEffY;
		
		state.internalAluOut = alu.evaluate(state.dataX, state.dataY, state.ct1Aluop) & (((long) 0xfffffff << 4) | 0xf);
		
		postMemoryStageState.nextForwardDataAluResult = state.internalAluOut;
		
		postMemoryStageState.nextForwardCt1Memtoreg = state.forwardCt1Memtoreg;
		postMemoryStageState.nextForwardCt1Regwrite = state.forwardCt1Regwrite;
		postMemoryStageState.nextForwardCt1DestRegAddress = (state.ct1Regdest == 1) ? state.ct1RdAddress : state.ct1RtAddress;
		
		postMemoryStageState.nextCt1Memwrite = state.forwardCt1Memwrite;
		postMemoryStageState.nextCt1Memread = state.forwardCt1Memread;
		postMemoryStageState.nextForwardCt1LinkAddress = state.forwardCt1Linkaddress;
		
		postMemoryStageState.nextForwardCt1Jal = state.forwardCt1Jal;
		
		postMemoryStageState.nextDataMemwritedata = state.dataEffY;
		
		postMemoryStageState.ct1Pcsrc = (state.internalAluOut == 1) ? 1 : 0;
		postMemoryStageState.ct1Pcsrc &= state.ct1Branch;
		
		int jtype = InstructionExtractor.instructionType(state.currentInstruction);
		
		state.ct1JumpTarget = (jtype == 7) ? (state.currentInstructionAddress & 0xF0000000) |
				(InstructionExtractor.jaddr(state.currentInstruction) << 2) 
				: state.dataRs;
				
		//TODO get ex_stall
		//Jump/branch taken, clear next IF stage / create a bubble
		if(state.ct1Jump == 1 | state.ct1Pcsrc == 1) //&& !ex_stall
		{
			//if_stall = true;
			//simFlags.add(SimulatorFlag.PLP_SIM_IF_STALL_SET);
		}
		
		bus.post(memoryPackage);
		//@formatter:on
	}
	
	@Override
	public void clock()
	{
		state.bubble = state.nextBubble;
		
		state.currentInstruction = state.nextInstruction;
		state.currentInstructionAddress = state.nextInstructionAddress;
		
		state.ct1Branch = state.nextCt1Branch;
		state.ct1Jump = state.nextCt1Jump;
		state.ct1Branchtarget = state.nextCt1BranchTarget;
		
		state.forwardCt1Memtoreg = state.nextForwardCt1Memtoreg;
		state.forwardCt1Regwrite = state.nextForwardCt1Regwrite;
		
		state.forwardCt1Memwrite = state.nextForwardCt1Memwrite;
		state.forwardCt1Memread = state.nextForwardCt1Memread;
		
		state.forwardCt1Jal = state.nextForwardCt1Jal;
		state.forwardCt1Linkaddress = state.nextForwardCt1LinkAddress;
		
		state.ct1Alusrc = state.nextCt1AluSrc;
		state.ct1Aluop = state.nextCt1AluOp;
		state.ct1Regdest = state.nextCt1Regdest;
		
		state.dataRs = state.nextDataRs;
		state.dataRt = state.nextDataRt;
		
		state.dataImmediateSignextended = state.nextDataImmediateSignExtended;
		state.ct1RtAddress = state.nextCt1RtAddress;
		state.ct1RdAddress = state.nextCt1RdAddress;
	}
	
	@Override
	public void printVariables()
	{
		/*
		 * String rt_forwarded = (sim_flags & (PLP_SIM_FWD_EX_EX_RT |
		 * PLP_SIM_FWD_MEM_EX_RT)) == 0 ? "" : " (forwarded)";
		 */
		
		/*
		 * String rs_forwarded = (sim_flags & (PLP_SIM_FWD_EX_EX_RS |
		 * PLP_SIM_FWD_MEM_EX_RS)) == 0 ? "" : " (forwarded)";
		 */
		int spaceSize = -35;
		
		System.out.println("EX vars");
		System.out.println(String.format("%" + spaceSize + "s %08x %s", "\tInstruction",
				state.currentInstruction,
				InstructionExtractor.format(state.currentInstruction)));
				
		String formattedInstructionAddress = ((state.currentInstructionAddress == -1
				|| state.bubble) ? "--------"
						: String.format("%08x", state.currentInstructionAddress));
		System.out.println(String.format("%" + spaceSize + "s %s", "\tInstructionAddress",
				formattedInstructionAddress));
				
		System.out.println(String.format("%" + spaceSize + "s %x", "\tForwardCt1MemToReg",
				state.forwardCt1Memtoreg));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tForwardCt1Regwrite",
				state.forwardCt1Regwrite));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tForwardCt1Memwrite",
				state.forwardCt1Memwrite));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tForwardCt1Memread",
				state.forwardCt1Memread));
		System.out.println(String.format("%" + spaceSize + "s %08x",
				"\tForwardCt1LinkAddress", state.forwardCt1Linkaddress));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tForwardCt1Jal",
				state.forwardCt1Jal));
				
		System.out.println(
				String.format("%" + spaceSize + "s %x", "\tct1AluSrc", state.ct1Alusrc));
		System.out.println(
				String.format("%" + spaceSize + "s %08x", "\tct1AluOp", state.ct1Aluop));
		System.out.println(
				String.format("%" + spaceSize + "s %x", "\tct1RegDst", state.ct1Regdest));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tct1AddressRt",
				state.ct1RtAddress));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tct1AddressRd",
				state.ct1RdAddress));
				
		System.out.println(String.format("%" + spaceSize + "s %08x", "\tct1Branchtarget",
				state.ct1Branchtarget));
		System.out.println(
				String.format("%" + spaceSize + "s %x", "\tct1Jump", state.ct1Jump));
		System.out.println(
				String.format("%" + spaceSize + "s %x", "\tct1Branch", state.ct1Branch));
		System.out.println(String.format("%" + spaceSize + "s %08x", "\tct1JumpTarget",
				state.ct1JumpTarget));
		System.out.println(
				String.format("%" + spaceSize + "s %x", "\tct1Pcsrc", state.ct1Pcsrc));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tct1ForwardX",
				state.ct1Forwardx));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tct1ForwardY",
				state.ct1Forwardy));
				
		System.out.println(String.format("%" + spaceSize + "s %08x",
				"\tDataImmediateSignExtended", state.dataImmediateSignextended));
		System.out.println(
				String.format("%" + spaceSize + "s %08x", "\tDataRs", state.dataRs));
		System.out.println(
				String.format("%" + spaceSize + "s %08x", "\tDataRt", state.dataRt));
		System.out.println(String.format("%" + spaceSize + "s %08x", "\tDataX (ALU0)*",
				state.dataX)); // + rs_forwarded
		System.out.println(
				String.format("%" + spaceSize + "s %08x", "\tDataEffY*", state.dataEffY)); // +
																							// rt_forwarded
		System.out.println(String.format("%" + spaceSize + "s %08x", "\tDataY (ALU1)*",
				state.dataY));
				
		System.out.println(String.format("%" + spaceSize + "s %08x", "\tInternalAluOut",
				state.internalAluOut));
		System.out.println();
	}
	
	@Override
	public void printNextVariables()
	{
		int spaceSize = -35;
		
		System.out.println("EX next vars");
		System.out.println(String.format("%" + spaceSize + "s %08x %s", "\tInstruction",
				state.nextInstruction,
				InstructionExtractor.format(state.nextInstruction)));
				
		String formattedInstructionAddress = ((state.currentInstructionAddress == -1)
				? "--------" : String.format("%08x", state.nextInstructionAddress));
		System.out.println(String.format("%" + spaceSize + "s %s", "\tInstructionAddress",
				formattedInstructionAddress));
				
		System.out.println(String.format("%" + spaceSize + "s %x",
				"\tNextForwardCt1MemToReg", state.nextForwardCt1Memtoreg));
		System.out.println(String.format("%" + spaceSize + "s %x",
				"\tNextForwardCt1Regwrite", state.nextForwardCt1Regwrite));
		System.out.println(String.format("%" + spaceSize + "s %x",
				"\tNextForwardCt1Memwrite", state.nextForwardCt1Memwrite));
		System.out.println(String.format("%" + spaceSize + "s %x",
				"\tNextForwardCt1Memread", state.nextForwardCt1Memread));
		System.out.println(String.format("%" + spaceSize + "s %08x",
				"\tForwardCt1LinkAddress", state.nextForwardCt1LinkAddress));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tNextForwardCt1Jal",
				state.nextForwardCt1Jal));
				
		System.out.println(String.format("%" + spaceSize + "s %x", "\tnextCt1AluSrc",
				state.nextCt1AluSrc));
		System.out.println(String.format("%" + spaceSize + "s %08x", "\tnextCt1AluOp",
				state.nextCt1AluOp));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tnextCt1RegDst",
				state.nextCt1Regdest));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tnextCt1AddressRt",
				state.nextCt1RtAddress));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tnextCt1AddressRd",
				state.nextCt1RdAddress));
				
		System.out.println(String.format("%" + spaceSize + "s %08x",
				"\tnextCt1Branchtarget", state.nextCt1BranchTarget));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tnextCt1Jump",
				state.nextCt1Jump));
		System.out.println(String.format("%" + spaceSize + "s %x", "\tnextCt1Branch",
				state.nextCt1Branch));
				
		System.out.println(String.format("%" + spaceSize + "s %08x",
				"\nextDataImmediateSignExtended", state.nextDataImmediateSignExtended));
		System.out.println(String.format("%" + spaceSize + "s %08x", "\nextDataRs",
				state.nextDataRs));
		System.out.println(String.format("%" + spaceSize + "s %08x", "\nextDataRt",
				state.nextDataRt));
		System.out.println();
	}
	
	@Override
	public String printInstruction()
	{
		String formattedInstructionAddress = (state.currentInstructionAddress == -1
				|| state.bubble) ? "--------"
						: String.format("08x", state.currentInstructionAddress);
						
		// TODO add MIPSInstr format like ability
		String instruction = String.format("%s %s %s %08x %s", "Execute:",
				formattedInstructionAddress, "Instruction:", state.currentInstruction,
				" : " + InstructionExtractor.format(state.currentInstruction));
				
		return instruction;
	}
	
	@Override
	public void reset()
	{
		state.count = 0;
	}
	
	@Override
	public boolean isHot()
	{
		return state.hot;
	}
	
	@Override
	public CpuState getState()
	{
		return state;
	}
	
	public class ExecuteEventHandler
	{
		private ExecuteEventHandler()
		{
		
		}
		
		public void instructionDecodeCompletionEvent(InstructionDecodeCompletion event)
		{
			CpuState postState = event.getPostState();
			
			if (event.willClearLogic())
			{
				postState.nextForwardCt1Memtoreg = 0;
				postState.nextForwardCt1Regwrite = 0;
				postState.nextForwardCt1Memwrite = 0;
				postState.nextForwardCt1Memread = 0;
				postState.nextForwardCt1Jal = 0;
				postState.nextCt1AluSrc = 0;
				postState.nextCt1Regdest = 0;
				postState.nextCt1Jump = 0;
				postState.nextCt1Branch = 0;
			}
			
			// TODO transfer state
		}
		
		public void stateRequested(ExecuteStageStateRequest event)
		{
			bus.post(new ExecuteStageStateResponse(state.clone()));
		}
		
		public void memoryStageStateResponse(MemoryStageStateResponse event)
		{
			currentMemoryStageState = event.getMemoryStageState();
		}
		
		public void writeBackStageStateResponse(WriteBackStageStateResponse event)
		{
			currentWriteBackStageState = event.getMemoryStageState();
		}
	}
	
}
