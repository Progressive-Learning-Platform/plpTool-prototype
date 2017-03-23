package edu.asu.plp.tool.backend.isa;

import edu.asu.plp.tool.backend.isa.exceptions.SimulatorException;

public interface Simulator
{
	boolean isRunning();
	
	void run();
	
	boolean step() throws SimulatorException;
	
	void reset();
	
	boolean isPaused();
	
	void pause();
	
	boolean isSimModeEnabled();
	
	boolean toggleSimMode();
	
	boolean isProgramLoaded();
	
	boolean loadProgram(ASMImage assembledImage);
	
	RegisterFile getRegisterFile();
	
	AddressBus getAddressBus();
	
	void setIRQ(long value);
	void maskIRQ(long value);
}
