/*
    DrMIPS - Educational MIPS simulator
    Copyright (C) 2013 Bruno Nova <ei08109@fe.up.pt>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.feup.brunonova.drmips.mips;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.feup.brunonova.drmips.exceptions.InfiniteLoopException;
import org.feup.brunonova.drmips.exceptions.InvalidCPUException;
import org.feup.brunonova.drmips.exceptions.InvalidInstructionSetException;
import org.feup.brunonova.drmips.mips.components.*;
import org.feup.brunonova.drmips.util.Dimension;
import org.feup.brunonova.drmips.util.Point;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class that represents and manipulates a simulated MIPS CPU.
 *
 * @author Bruno Nova
 */
public class CPU {
	/** The path to the CPU files, with the trailing slash. */
	public static final String FILENAME_PATH = "cpu/";
	/** The file extension of the CPU files. */
	public static final String FILENAME_EXTENSION = "cpu";
	/** The regular expression to validate register names. */
	public static final String REGNAME_REGEX = "^[a-zA-Z][a-zA-Z\\d]*$";
	/** The prefix char of the registers. */
	public static final char REGISTER_PREFIX = '$';
	/** The extra margin added to the width of the graphical CPU's size. */
	public static final int RIGHT_MARGIN = 10;
	/** The extra margin added to the height of the graphical CPU's size. */
	public static final int BOTTOM_MARGIN = 10;
	/** The unit used in latencies. */
	public static final String LATENCY_UNIT = "ps";
	/** The number of clock cycles executed in <tt>executeAll()</tt> after which it throws an exception. */
	public static final int EXECUTE_ALL_LIMIT_CYCLES = 1000;
	
	/** The file of the CPU. */
	private File file = null;
	/** The components that the CPU contains. */
	private Map<String, Component> components;
	/** The components that are synchronous (convenience list). */
	private List<Component> synchronousComponents;
	/** The names of the registers (without the prefix). */
	private List<String> registerNames = null;
	/** The loaded instruction set. */
	private InstructionSet instructionSet = null;
	/** The assembler for this CPU. */
	private Assembler assembler = null;
	/** The Program Counter (set automatically in <tt>addComponent()</tt>. */
	private PC pc = null;
	/** The register bank (set automatically in <tt>addComponent()</tt>. */
	private RegBank regbank = null;
	/** The instruction memory (set automatically in <tt>addComponent()</tt>. */
	private InstructionMemory instructionMemory = null;
	/** The control unit (set automatically in <tt>addComponent()</tt>. */
	private ControlUnit controlUnit = null;
	/** The ALU controller (set automatically in <tt>addComponent()</tt>. */
	private ALUControl aluControl = null;
	/** The ALU (set automatically in <tt>addComponent()</tt>. */
	private ALU alu = null;
	/** The data memory (set automatically in <tt>addComponent()</tt>. */
	private DataMemory dataMemory = null;
	/** The forwarding unit (set automatically in <tt>addComponent()</tt>. */
	private ForwardingUnit forwardingUnit = null;
	/** The hazard detection unit (set automatically in <tt>addComponent()</tt>. */
	private HazardDetectionUnit hazardDetectionUnit = null;
	/** The IF/ID register, if the CPU is pipelined. */
	private PipelineRegister ifIdReg = null;
	/** The ID/EX register, if the CPU is pipelined. */
	private PipelineRegister idExReg = null;
	/** The EX/MEM register, if the CPU is pipelined. */
	private PipelineRegister exMemReg = null;
	/** The MEM/WB register, if the CPU is pipelined. */
	private PipelineRegister memWbReg = null;
	
	/**
	 * Constructor that should by called by other constructors.
	 */
	protected CPU() {
		components = new TreeMap<String, Component>();
		synchronousComponents = new LinkedList<Component>();
		assembler = new Assembler(this);
	}
	
	/**
	 * Constructor that should by called by other constructors.
	 * @param file The file of the CPU.
	 */
	protected CPU(File file) {
		this();
		setFile(file);
	}
	
	/**
	 * Creates a CPU from a JSON file.
	 * @param path Path to the JSON file.
	 * @return CPU created from the file.
	 * @throws IOException If the file doesn't exist or an I/O error occurs.
	 * @throws JSONException If the JSON file is malformed.
	 * @throws InvalidCPUException If the CPU is invalid or incomplete
	 * @throws ArrayIndexOutOfBoundsException If an array index is invalid somewhere (like an invalid register).
	 * @throws InvalidInstructionSetException If the instruction set is invalid.
	 * @throws NumberFormatException If an opcode is not a number.
	 */
	public static CPU createFromJSONFile(String path) throws IOException, JSONException, InvalidCPUException, ArrayIndexOutOfBoundsException, InvalidInstructionSetException, NumberFormatException {
		CPU cpu = new CPU(new File(path));
		BufferedReader reader = null;
		String file = "", line, parentPath = ".";

		// Read file to String
		try {
			File f = new File(path);
			parentPath = f.getParentFile().getAbsolutePath();
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));
			while((line = reader.readLine()) != null) 
				file += line + "\n";
			reader.close();
		}
		catch(IOException e) {
			throw e;
		}
		finally {
			if(reader != null) reader.close();
		}
		
		// Parse the JSON file
		JSONObject json = new JSONObject(file);
		parseJSONComponents(cpu, json.getJSONObject("components"));
		cpu.checkRequiredComponents();
		if(cpu.hasForwardingUnit()) cpu.forwardingUnit.setRegbank(cpu.getRegBank());
		if(cpu.hasHazardDetectionUnit()) cpu.hazardDetectionUnit.setRegbank(cpu.getRegBank());
		if(json.has("reg_names")) parseJSONRegNames(cpu, json.getJSONArray("reg_names"));
		cpu.instructionSet = new InstructionSet(parentPath + File.separator + json.getString("instructions"));
		cpu.controlUnit.setControl(cpu.getInstructionSet().getControl(), cpu.getInstructionSet().getOpCodeSize());
		if(cpu.hasALUControl()) cpu.aluControl.setControl(cpu.getInstructionSet().getControlALU());
		if(cpu.hasALU()) cpu.alu.setControl(cpu.getInstructionSet().getControlALU());
		parseJSONWires(cpu, json.getJSONArray("wires"));
		cpu.calculatePerformance();
		cpu.determineControlPath();
		
		for(Component c: cpu.getComponents()) // "execute" all components (initialize all outputs/inputs)
			c.execute();
		
		return cpu;
	}
	
	private void checkRequiredComponents() throws InvalidCPUException {
		if(pc == null) throw new InvalidCPUException("The program counter is required!");
		if(regbank == null) throw new InvalidCPUException("The register bank is required!");
		if(instructionMemory == null) throw new InvalidCPUException("The instruction memory is required!");
		if(controlUnit == null) throw new InvalidCPUException("The control unit is required!");
		
		// Check number of pipeline registers (must be 0 or 4)
		int count = 0;
		for(Component c: getComponents())
			if(c instanceof PipelineRegister) count++;
		if(count > 0 && count != 4)
			throw new InvalidCPUException("Pipelined CPUs must have exactly 4 pipeline registers (5 stages)!");
	}
	
	/**
	 * Calculates the latency in each component and input and determines the critical path.
	 */
	public final void calculatePerformance() {
		for(Component c: getComponents()) // reset latencies and critical path
			c.resetPerformance();
		
		for(Component c: synchronousComponents) // calculate latencies
			c.updateAccumulatedLatency();
		
		determineCriticalPath();
	}
	
	/**
	 * Resets the latencies of all the components to their original latencies.
	 */
	public final void resetLatencies() {
		for(Component c: getComponents())
			c.resetLatency();
		
		calculatePerformance();
	}
	
	/**
	 * Determines the CPU's critical path
	 */
	private void determineCriticalPath() {
		// Find the highest accumulated latency
		int maxLatency = 0;
		for(Component c: getComponents()) {
			if(c.getAccumulatedLatency() > maxLatency)
				maxLatency = c.getAccumulatedLatency();
			for(Input i: c.getInputs()) {
				if(i.getAccumulatedLatency() > maxLatency)
					maxLatency = i.getAccumulatedLatency();
			}
		}
		
		// Calculate critical path, starting in the components/inputs with maxLatency and going backwards
		for(Component c: getComponents()) {
			if(c.getAccumulatedLatency() == maxLatency) { // component with maxLatency?
				determineCriticalPath(c);
			}
			
			for(Input i: c.getInputs()) {
				if(i.getAccumulatedLatency() == maxLatency && i.isConnected() && !i.getConnectedOutput().isInCriticalPath()) { // input with maxLatency?
					i.getConnectedOutput().setInCriticalPath();
					determineCriticalPath(i.getConnectedOutput().getComponent());
				}
			}
		}
	}
	
	/**
	 * Determines the critical path up to the specified component (recursively).
	 * @param c The component.
	 */
	private void determineCriticalPath(Component component) {
		int lat = component.getAccumulatedLatency() - component.getLatency();
		for(Input i: component.getInputs()) {
			if(i.canChangeComponentAccumulatedLatency() && i.getAccumulatedLatency() == lat 
				&& i.isConnected() && !i.getConnectedOutput().isInCriticalPath()) {
				i.getConnectedOutput().setInCriticalPath();
				determineCriticalPath(i.getConnectedOutput().getComponent());
			}
		}
	}
	
	/**
	 * Updates the list of components and wires that are in the control path.
	 */
	public final void determineControlPath() {
		controlUnit.setInControlPath();
		if(alu != null) alu.getZero().setInControlPath();
		if(aluControl != null) aluControl.setInControlPath();
		if(forwardingUnit != null) forwardingUnit.setInControlPath();
		if(hazardDetectionUnit != null) hazardDetectionUnit.setInControlPath();
	}
	
	/**
	 * Returns whether the CPU is pipelined.
	 * @return <tt>True</tt> if the CPU is pipelined.
	 */
	public boolean isPipeline() {
		return ifIdReg != null;
	}
	
	/**
	 * Sets the file of the CPU.
	 * @param file The file.
	 */
	public final void setFile(File file) {
		this.file = file;
	}
	
	/**
	 * Returns the file of the CPU.
	 * @return The file of the CPU.
	 */
	public final File getFile() {
		return file;
	}
	
	/**
	 * Returns the graphical size of the CPU.
	 * <p>The size is calculated here, so avoid calling this method repeteadly!</p>
	 * @return Size of the graphical CPU.
	 */
	public Dimension getSize() {
		int x, y, width, height;
		width = height = 0;
		
		for(Component c: getComponents()) { // check each component's position + size and output wires points
			// Component's position + size
			x = c.getPosition().x + c.getSize().width;
			y = c.getPosition().y + c.getSize().height;
			if(x > width) width = x;
			if(y > height) height = y;
			
			// Component's outputs points
			for(Output o: c.getOutputs()) {
				if(o.isConnected()) {
					o.getPosition();
					if(o.getPosition() != null) { // start point
						x = o.getPosition().x;
						y = o.getPosition().y;
						if(x > width) width = x;
						if(y > height) height = y;
					}
					if(o.getConnectedInput().getPosition() != null) { // end point
						x = o.getConnectedInput().getPosition().x;
						y = o.getConnectedInput().getPosition().y;
						if(x > width) width = x;
						if(y > height) height = y;
					}
					for(Point p: o.getIntermediatePoints()) { // intermediate points
						if(p.x > width) width = p.x;
						if(p.y > height) height = p.y;
					}
				}
			}
		}
		
		return new Dimension(width + RIGHT_MARGIN, height + BOTTOM_MARGIN);
	}
	
	/**
	 * Returns whether the currently loaded program has finished executing.
	 * @return <tt>True</tt> it the program has finished.
	 */
	public boolean isProgramFinished() {
		if(isPipeline())
			return pc.getCurrentInstructionIndex() == -1 && ifIdReg.getCurrentInstructionIndex() == -1
				&& idExReg.getCurrentInstructionIndex() == -1 && exMemReg.getCurrentInstructionIndex() == -1
				&& memWbReg.getCurrentInstructionIndex() == -1;
		else
			return pc.getCurrentInstructionIndex() == -1;
	}
	
	/**
	 * Executes the currently loaded program until the end.
	 * @throws InfiniteLoopException If the <tt>EXECUTE_ALL_LIMIT_CYCLES</tt> limit has been reached (possible infinite loop).
	 */
	public void executeAll() throws InfiniteLoopException {
		int cycles = 0;
		while(!isProgramFinished()) {
			if(cycles++ > EXECUTE_ALL_LIMIT_CYCLES) // prevent possible infinite cycles
				throw new InfiniteLoopException();
			executeCycle();
		}
	}
	
	/**
	 * "Executes" a clock cycle (a step).
	 */
	public void executeCycle() {
		saveCycleState();
		for(Component c: synchronousComponents) // execute synchronous actions without propagating output changes
			((IsSynchronous)c).executeSynchronous();
		
		// Store index(es) of the instruction(s) being executed
		int index = getPC().getAddress().getValue() / (Data.DATA_SIZE / 8);
		if(index < 0 || index >= getInstructionMemory().getNumberOfInstructions())
			index = -1;
		if(isPipeline()) { // save other instructions in pipeline
			updatePipelineRegisterCurrentInstruction(memWbReg, exMemReg.getCurrentInstructionIndex());
			updatePipelineRegisterCurrentInstruction(exMemReg, idExReg.getCurrentInstructionIndex());
			updatePipelineRegisterCurrentInstruction(idExReg, ifIdReg.getCurrentInstructionIndex());
			updatePipelineRegisterCurrentInstruction(ifIdReg, pc.getCurrentInstructionIndex());
		}
		getPC().setCurrentInstructionIndex(index);
		
		for(Component c: synchronousComponents) // execute normal actions, propagating output changes
			c.execute();
		for(Component c: getComponents()) // "execute" all components, just to be safe
			c.execute();
	}
	
	/**
	 * Updates the current instruction index stored in the specified pipeline register.
	 * @param reg The pipeline register to update.
	 * @param previousIndex The index of the instruction in the previous stage.
	 */
	private void updatePipelineRegisterCurrentInstruction(PipelineRegister reg, int previousIndex) {
		if(reg.getFlush().getValue() == 1)
				reg.setCurrentInstructionIndex(-1);
			else if(reg.getWrite().getValue() == 1)
				reg.setCurrentInstructionIndex(previousIndex);
	}
	
	/**
	 * Updates the program counter to a new address.
	 * <p>This method also changes the current instruction index to a correct value,
	 * so call this method instead of <tt>getPC().setAddress()</tt>!</p>
	 * @param address The new address.
	 */
	public void setPCAddress(int address) {
		getPC().setAddress(address); // reset PC
		int index = address / (Data.DATA_SIZE / 8);
		if(index >= 0 && index < getInstructionMemory().getNumberOfInstructions())
			getPC().setCurrentInstructionIndex(index);
		else
			getPC().setCurrentInstructionIndex(-1);
	}
	
	/**
	 * Saves the state of the current cycle.
	 */
	public void saveCycleState() {
		for(Component c: synchronousComponents)
			((IsSynchronous)c).pushState();
	}
	
	/**
	 * Performs a "step back" in the execution if possible (if <tt>hasPreviousCycle() == true</tt>).
	 */
	public void restorePreviousCycle() {
		if(hasPreviousCycle()) {
			for(Component c: synchronousComponents) // restore previous states
				((IsSynchronous)c).popState();
			for(Component c: synchronousComponents) // execute normal actions, propagating output changes
				c.execute();
			for(Component c: getComponents()) // "execute" all components
				c.execute();
		}
	}
	
	/**
	 * Returns whether there was a previous cycle executed.
	 * @return <tt>True</tt> if a "step back" is possible (<tt>getPc().hasSavedStates() == true</tt>).
	 */
	public boolean hasPreviousCycle() {
		if(pc != null)
			return pc.hasSavedStates();
		else
			return false;
	}
	
	/**
	 * Removes all the saved previous cycles.
	 */
	public void clearPreviousCycles() {
		for(Component c: synchronousComponents)
			((IsSynchronous)c).clearSavedStates();
	}
	
	/**
	 * Resets the states of the CPU's components to the first cycle.
	 */
	public void resetToFirstCycle() {
		if(hasPreviousCycle()) {
			for(Component c: synchronousComponents) // restore first state
				((IsSynchronous)c).resetFirstState();
			for(Component c: synchronousComponents) // execute normal actions, propagating output changes
				c.execute();
			for(Component c: getComponents()) // "execute" all components
				c.execute();
		}
	}
	
	/**
	 * Resets the stored data of the CPU to zeros (register bank and data memory).
	 */
	public void resetData() {
		regbank.reset();
		if(hasDataMemory()) dataMemory.reset();
		if(hasALU() && alu instanceof ExtendedALU) ((ExtendedALU)alu).reset();
	}
	
	/**
	 * Connects the given output to the given input.
	 * @param output Output to connect from.
	 * @param input Input to connect to.
	 * @return The resulting output.
	 * @throws InvalidCPUException If the output or the input are already connected or have different sizes.
	 */
	protected Output connectComponents(Output output, Input input) throws InvalidCPUException {
		output.connectTo(input);
		return output;
	}
	
	/**
	 * Connects the given output to the given input.
	 * @param outCompId The identifier of the output component.
	 * @param outId The identifier of the output of the output component.
	 * @param inCompId The identifier of the input component.
	 * @param inId The identifier of the input of the input component.
	 * @return The resulting output.
	 * @throws InvalidCPUException If the output or the input are already connected or have different sizes or don't exist.
	 */
	protected Output connectComponents(String outCompId, String outId, String inCompId, String inId) throws InvalidCPUException {
		Component out = getComponent(outCompId);
		Component in = getComponent(inCompId);
		if(out == null) throw new InvalidCPUException("Unknown ID " + outCompId + "!");
		if(in == null) throw new InvalidCPUException("Unknown ID " + inCompId + "!");
		
		Output o = out.getOutput(outId);
		Input i = in.getInput(inId);
		if(o == null) throw new InvalidCPUException("Unknown ID " + outId + "!");
		if(i == null) throw new InvalidCPUException("Unknown ID " + inId + "!");

		o.connectTo(i);
		return o;
	}
	
	/**
	 * Returns whether a component with the specified identifier exists.
	 * @param id Component identifier.
	 * @return <tt>true</tt> if the component exists.
	 */
	public final boolean hasComponent(String id) {
		return components.containsKey(id);
	}
	
	/**
	 * Returns the component with the specified identifier.
	 * @param id Component identifier.
	 * @return The desired component, or <tt>null</tt> if it doesn't exist.
	 */
	public final Component getComponent(String id) {
		return components.get(id);
	}
	
	/**
	 * Returns an array with all the coponents.
	 * @return Array with all components.
	 */
	public Component[] getComponents() {
		Component[] c = new Component[components.size()];
		return components.values().toArray(c);
	}
	
	/**
	 * Adds the specified component to the CPU.
	 * @param component The component to add.
	 * @throws InvalidCPUException If the new component makes the CPU invalid.
	 */
	protected final void addComponent(Component component) throws InvalidCPUException {
		if(hasComponent(component.getId())) throw new InvalidCPUException("Duplicated ID " + component.getId() + "!");
		components.put(component.getId(), component);
		if(component instanceof IsSynchronous)
			synchronousComponents.add(component);
		if(component instanceof PC) {
			if(pc != null) throw new InvalidCPUException("Only one program counter allowed!");
			pc = (PC)component;
		}
		else if(component instanceof RegBank) {
			if(regbank != null) throw new InvalidCPUException("Only one register bank allowed!");
			regbank = (RegBank)component;
		}
		else if(component instanceof InstructionMemory) {
			if(instructionMemory != null) throw new InvalidCPUException("Only one instruction memory allowed!");
			instructionMemory = (InstructionMemory)component;
		}
		else if(component instanceof ControlUnit) {
			if(controlUnit != null) throw new InvalidCPUException("Only one control unit allowed!");
			controlUnit = (ControlUnit)component;
		}
		else if(component instanceof ALUControl) {
			if(aluControl != null) throw new InvalidCPUException("Only one ALU control allowed!");
			aluControl = (ALUControl)component;
		}
		else if(component instanceof ALU) {
			if(alu != null) throw new InvalidCPUException("Only one ALU allowed!");
			alu = (ALU)component;
		}
		else if(component instanceof DataMemory) {
			if(dataMemory != null) throw new InvalidCPUException("Only one data memory allowed!");
			dataMemory = (DataMemory)component;
		}
		else if(component instanceof ForwardingUnit) {
			if(forwardingUnit != null) throw new InvalidCPUException("Only one forwarding unit allowed!");
			forwardingUnit = (ForwardingUnit)component;
		}
		else if(component instanceof HazardDetectionUnit) {
			if(hazardDetectionUnit != null) throw new InvalidCPUException("Only one hazard detection unit allowed!");
			hazardDetectionUnit = (HazardDetectionUnit)component;
		}
		else if(component instanceof PipelineRegister) {
			String id = component.getId().trim().toUpperCase();
			if(id.equals("IF/ID")) {
				if(ifIdReg != null) throw new InvalidCPUException("Only one IF/ID pipeline register allowed!");
				ifIdReg = (PipelineRegister)component;
			}
			else if(id.equals("ID/EX")) {
				if(idExReg != null) throw new InvalidCPUException("Only one ID/EX pipeline register allowed!");
				idExReg = (PipelineRegister)component;
			}
			else if(id.equals("EX/MEM")) {
				if(exMemReg != null) throw new InvalidCPUException("Only one EX/MEM pipeline register allowed!");
				exMemReg = (PipelineRegister)component;
			}
			else if(id.equals("MEM/WB")) {
				if(memWbReg != null) throw new InvalidCPUException("Only one MEM/WB pipeline register allowed!");
				memWbReg = (PipelineRegister)component;
			}
			else
				throw new InvalidCPUException("A pipeline register's identifier must be one of {IF/ID, ID/EX, EX/MEM, MEM/WB}!");
		}
	}
	
	/**
	 * Returns the loaded instruction set.
	 * @return Loaded instruction set.
	 */
	public final InstructionSet getInstructionSet() {
		return instructionSet;
	}
	
	/**
	 * Returns the Program Counter.
	 * @return Program Counter.
	 */
	public final PC getPC() {
		return pc;
	}
	
	/**
	 * Returns the register bank.
	 * @return Register bank.
	 */
	public final RegBank getRegBank() {
		return regbank;
	}
	
	/**
	 * Returns the instruction memory.
	 * @return Instruction memory.
	 */
	public final InstructionMemory getInstructionMemory() {
		return instructionMemory;
	}
	
	/**
	 * Returns the control unit.
	 * @return Control unit.
	 */
	public final ControlUnit getControlUnit() {
		return controlUnit;
	}
	
	/**
	 * Returns the ALU control.
	 * @return ALU control.
	 */
	public final ALUControl getALUControl() {
		return aluControl;
	}
	
	/**
	 * Returns whether the CPU contains an ALU control.
	 * @return <tt>True</tt> if an ALU control exists.
	 */
	public final boolean hasALUControl() {
		return aluControl != null;
	}
	
	/**
	 * Returns the ALU.
	 * @return ALu.
	 */
	public final ALU getALU() {
		return alu;
	}
	
	/**
	 * Returns whether the CPU contains an ALU.
	 * @return <tt>True</tt> if an ALU exists.
	 */
	public final boolean hasALU() {
		return alu != null;
	}
	
	/**
	 * Returns the data memory.
	 * @return Data memory.
	 */
	public final DataMemory getDataMemory() {
		return dataMemory;
	}
	
	/**
	 * Returns whether the CPU contains data memory.
	 * @return <tt>True</tt> if a data memory exists.
	 */
	public final boolean hasDataMemory() {
		return dataMemory != null;
	}
	
	/**
	 * Returns the forwarding unit.
	 * @return Forwarding unit.
	 */
	public final ForwardingUnit getForwardingUnit() {
		return forwardingUnit;
	}
	
	/**
	 * Returns whether the CPU contains a forwarding unit.
	 * @return <tt>True</tt> if a forwarding unit exists.
	 */
	public final boolean hasForwardingUnit() {
		return forwardingUnit != null;
	}
	
	/**
	 * Returns the hazard detection unit.
	 * @return Hazard detection unit.
	 */
	public final HazardDetectionUnit getHazardDetectionUnit() {
		return hazardDetectionUnit;
	}
	
	/**
	 * Returns whether the CPU contains a hazard detection unit.
	 * @return <tt>True</tt> if a hazard detection unit exists.
	 */
	public final boolean hasHazardDetectionUnit() {
		return hazardDetectionUnit != null;
	}
	
	/**
	 * Returns the IF/ID pipeline register.
	 * @return IF/ID pipeline register, or <tt>null</tt> if not pipeline.
	 */
	public final PipelineRegister getIfIdReg() {
		return ifIdReg;
	}
	
	/**
	 * Returns the ID/EX pipeline register.
	 * @return ID/EX pipeline register, or <tt>null</tt> if not pipeline.
	 */
	public final PipelineRegister getIdExReg() {
		return idExReg;
	}
	
	/**
	 * Returns the EX/MEM pipeline register.
	 * @return EX/MEM pipeline register, or <tt>null</tt> if not pipeline.
	 */
	public final PipelineRegister getExMemReg() {
		return exMemReg;
	}
	
	/**
	 * Returns the MEM/WB pipeline register.
	 * @return MEM/WB pipeline register, or <tt>null</tt> if not pipeline.
	 */
	public final PipelineRegister getMemWbReg() {
		return memWbReg;
	}
	
	/**
	 * Returns the index/address of the register with the specified name.
	 * @param name Name of the register (with prefix).
	 * @return The index of the register, or -1 if it doesn't exist.
	 */
	public int getRegisterIndex(String name) {
		name = name.trim().toLowerCase();
		if(name.length() < 2 || name.charAt(0) != REGISTER_PREFIX)
			return -1;
		name = name.substring(1);
		try {
			int index = Integer.parseInt(name);
			// Numeric name (like $0)
			if(index >= 0 && index < regbank.getNumberOfRegisters())
				return index;
			else
				return -1;
		}
		catch(NumberFormatException e) {
			// Register name (like $zero)
			if(registerNames != null)
				return registerNames.indexOf(name);
			else
				return -1;
		}
	}
	
	/**
	 * Returns the name of the register with the specified index/address.
	 * @param index The index of the register.
	 * @return The name of the register (with prefix).
	 * @throws IndexOutOfBoundsException If the register with the given index doesn't exist.
	 */
	public String getRegisterName(int index) throws IndexOutOfBoundsException {
		if(registerNames != null)
			return REGISTER_PREFIX + registerNames.get(index);
		else
			return REGISTER_PREFIX + "" + index;
	}
	
	/**
	 * Returns whether a register with the given name exists.
	 * @param name Name of the register (with prefix).
	 * @return <tt>true</tt> if the register exists.
	 */
	public boolean hasRegister(String name) {
		return getRegisterIndex(name) != -1;
	}
	
	/**
	 * Returns the assembler for this CPU.
	 * @return The assembler for this CPU.
	 */
	public Assembler getAssembler() {
		return assembler;
	}
	
	/**
	 * Parses and creates the components from the given JSON array.
	 * @param cpu The CPU to add the components to.
	 * @param components JSONObject that contains the components array.
	 * @throws JSONException If the JSON file is malformed.
	 * @throws InvalidCPUException If the CPU is invalid or incomplete.
	 * @throws ArrayIndexOutOfBoundsException If an array index is invalid somewhere (like an invalid register).
	 */
	private static void parseJSONComponents(CPU cpu, JSONObject components) throws JSONException, InvalidCPUException, ArrayIndexOutOfBoundsException {
		JSONObject comp, d;
		Component.Type type;
		String typeOrig, id, lang;
		int latency;
		LinkedList<String> ids;
		JSONArray outs, ins;
		Point pos;
		Iterator<String> i = components.keys(), j;
		Component component = null;
		
		while(i.hasNext()) {
			id = i.next();
			comp = components.getJSONObject(id);
			typeOrig = comp.getString("type");
			latency = comp.optInt("latency", 0);
			pos = new Point(comp.getInt("x"), comp.getInt("y"));
			
			try {
				type = Component.Type.valueOf(typeOrig.toUpperCase());
			}
			catch(IllegalArgumentException e) {
				throw new InvalidCPUException("Unknown component type " + typeOrig + "!");
			}
			
			switch(type) { 
				case PC: cpu.addComponent(component = new PC(id, latency, pos, comp.getString("in"), comp.getString("out"), comp.optString("write", "Write"))); break;
				case ADD: cpu.addComponent(component = new Add(id, latency, pos, comp.getString("in1"), comp.getString("in2"), comp.getString("out"))); break;
				case AND: cpu.addComponent(component = new And(id, latency, pos, comp.getString("in1"), comp.getString("in2"), comp.getString("out"))); break;
				case OR: cpu.addComponent(component = new Or(id, latency, pos, comp.getString("in1"), comp.getString("in2"), comp.getString("out"))); break;
				case XOR: cpu.addComponent(component = new Xor(id, latency, pos, comp.getString("in1"), comp.getString("in2"), comp.getString("out"))); break;
				case NOT: cpu.addComponent(component = new Not(id, latency, pos, comp.getString("in"), comp.getString("out"))); break;
				case REGBANK:
					RegBank regbank = new RegBank(id, latency, pos, comp.getInt("num_regs"), comp.getString("read_reg1"), comp.getString("read_reg2"), comp.getString("read_data1"), comp.getString("read_data2"), comp.getString("write_reg"), comp.getString("write_data"), comp.getString("reg_write"), comp.optBoolean("forwarding"));
					if(comp.has("const_regs")) {
						JSONArray regs = comp.getJSONArray("const_regs");
						JSONObject reg;
						for(int x = 0; x < regs.length(); x++) {
							if((reg = regs.optJSONObject(x)) != null) // object with "reg" and "val"
								regbank.setRegisterConstant(reg.getInt("reg"), reg.optInt("val"));
							else // an integer
								regbank.setRegisterConstant(regs.getInt(x));
						}
					}
					cpu.addComponent(component = regbank);
					break;
				case IMEM: cpu.addComponent(component = new InstructionMemory(id, latency, pos, comp.getString("in"), comp.getString("out"))); break;
				case FORK:
					ids = new LinkedList<String>();
					outs = comp.optJSONArray("out");
					for(int x = 0; x < outs.length(); x++)
						ids.add(outs.getString(x));
					cpu.addComponent(component = new Fork(id, latency, pos, comp.getInt("size"), comp.getString("in"), ids));
					break;
				case CONTROL: cpu.addComponent(component = new ControlUnit(id, latency, pos, comp.getString("in"))); break;
				case DIST:
					JSONObject inD = comp.getJSONObject("in");
					Distributor dist = new Distributor(id, latency, pos, inD.getString("id"), inD.getInt("size"));
					outs = comp.getJSONArray("out");
					JSONObject outD;
					int msb, lsb;
					for(int x = 0; x < outs.length(); x++) {
						outD = outs.getJSONObject(x);
						msb = outD.getInt("msb");
						lsb = outD.getInt("lsb");
						dist.addOutput(outD.optString("id", msb + "-" + lsb), msb, lsb);
					}
					cpu.addComponent(component = dist);
					break;
				case MUX:
					ids = new LinkedList<String>();
					ins = comp.optJSONArray("in");
					for(int x = 0; x < ins.length(); x++)
						ids.add(ins.getString(x));
					cpu.addComponent(component = new Multiplexer(id, latency, pos, comp.getInt("size"), ids, comp.getString("sel"), comp.getString("out")));
					break;
				case CONST: cpu.addComponent(component = new Constant(id, latency, pos, comp.getString("out"), comp.getInt("val"), comp.getInt("size"))); break;
				case SEXT: cpu.addComponent(component = new SignExtend(id, latency, pos, comp.getJSONObject("in").getString("id"), comp.getJSONObject("in").getInt("size"), comp.getJSONObject("out").getString("id"), comp.getJSONObject("out").getInt("size"))); break;
				case ZEXT: cpu.addComponent(component = new ZeroExtend(id, latency, pos, comp.getJSONObject("in").getString("id"), comp.getJSONObject("in").getInt("size"), comp.getJSONObject("out").getString("id"), comp.getJSONObject("out").getInt("size"))); break;
				case SLL: cpu.addComponent(component = new ShiftLeft(id, latency, pos, comp.getJSONObject("in").getString("id"), comp.getJSONObject("in").getInt("size"), comp.getJSONObject("out").getString("id"), comp.getJSONObject("out").getInt("size"), comp.getInt("amount"))); break;
				case CONCAT: cpu.addComponent(component = new Concatenator(id, latency, pos, comp.getJSONObject("in1").getString("id"), comp.getJSONObject("in1").getInt("size"), comp.getJSONObject("in2").getString("id"), comp.getJSONObject("in2").getInt("size"), comp.getString("out"))); break;
				case ALU_CONTROL: cpu.addComponent(component = new ALUControl(id, latency, pos, comp.getString("aluop"), comp.getString("func"))); break;
				case ALU: cpu.addComponent(component = new ALU(id, latency, pos, comp.getString("in1"), comp.getString("in2"), comp.getString("control"), comp.getString("out"), comp.getString("zero"))); break;
				case EXT_ALU: cpu.addComponent(component = new ExtendedALU(id, latency, pos, comp.getString("in1"), comp.getString("in2"), comp.getString("control"), comp.getString("out"), comp.getString("zero"))); break;
				case DMEM: cpu.addComponent(component = new DataMemory(id, latency, pos, comp.getInt("size"), comp.getString("address"), comp.getString("write_data"), comp.getString("out"), comp.getString("mem_read"), comp.getString("mem_write"))); break;
				case PIPEREG:
					Map<String, Integer> regs = new TreeMap<String, Integer>();
					JSONObject r = comp.optJSONObject("regs");
					if(r != null) {
						Iterator<String> k = r.keys();
						String key;
						while(k.hasNext()) {
							key = k.next();
							regs.put(key, r.getInt(key));
						}
					}
					cpu.addComponent(component = new PipelineRegister(id, latency, pos, regs, comp.optString("write", "Write"), comp.optString("flush", "Flush")));
					break;
				case FWD_UNIT: cpu.addComponent(component = new ForwardingUnit(id, latency, pos, comp.getString("ex_mem_reg_write"), comp.getString("mem_wb_reg_write"), comp.getString("ex_mem_rd"), comp.getString("mem_wb_rd"), comp.getString("id_ex_rs"), comp.getString("id_ex_rt"), comp.getString("fwd_a"), comp.getString("fwd_b"))); break;
				case HZD_UNIT: cpu.addComponent(component = new HazardDetectionUnit(id, latency, pos, comp.getString("id_ex_mem_read"), comp.getString("id_ex_rt"), comp.getString("if_id_rs"), comp.getString("if_id_rt"), comp.getString("stall"))); break;
				default: throw new InvalidCPUException("Unknown component type " + typeOrig + "!");
			}
			
			// Custom descriptions, if any
			if(component != null && (d = comp.optJSONObject("desc")) != null) {
				j = d.keys();
				while(j.hasNext()) {
					lang = j.next();
					component.addCustomDescriptions(lang, d.getString(lang));
				}
			}
		}
	}
	
	/**
	 * Parses wires from the given JSON array and connects the components.
	 * @param cpu The CPU to add wires to.
	 * @param wires JSONArray that contains the wires array.
	 * @throws JSONException If the JSON file is malformed.
	 * @throws InvalidCPUException If the output or the input are already connected or have different sizes or doesn't exist.
	 */
	private static void parseJSONWires(CPU cpu, JSONArray wires) throws JSONException, InvalidCPUException {
		JSONObject wire, point, start, end;
		JSONArray points;
		Output out;
		
		for(int i = 0; i < wires.length(); i++) {
			wire = wires.getJSONObject(i);
			out = cpu.connectComponents(wire.getString("from"), wire.getString("out"), 
				wire.getString("to"), wire.getString("in"));
			points = wire.optJSONArray("points");
			if(points != null) {
				for(int x = 0; x < points.length(); x++) {
					point = points.getJSONObject(x);
					out.addIntermediatePoint(new Point(point.getInt("x"), point.getInt("y")));
				}
			}
			if((start = wire.optJSONObject("start")) != null)
				out.setPosition(new Point(start.getInt("x"), start.getInt("y")));
			if((end = wire.optJSONObject("end")) != null)
				out.getConnectedInput().setPosition(new Point(end.getInt("x"), end.getInt("y")));
		}
	}
	
	/**
	 * Parses and sets the identifiers of the registers.
	 * @param cpu The CPU to set the registers informations.
	 * @param regs JSONArray that contains the registers.
	 * @throws JSONException If the JSON file is malformed.
	 * @throws InvalidCPUException If not all registers are specified or a register is invalid.
	 */
	private static void parseJSONRegNames(CPU cpu, JSONArray regs) throws JSONException, InvalidCPUException {
		if(regs.length() != cpu.getRegBank().getNumberOfRegisters())
			throw new InvalidCPUException("Not all registers have been specified in the registers block!");
		cpu.registerNames = new ArrayList<String>(cpu.getRegBank().getNumberOfRegisters());
		String id;
		
		for(int i = 0; i < regs.length(); i++) {
			id = regs.getString(i).trim().toLowerCase();
			
			if(id.isEmpty()) 
				throw new InvalidCPUException("Invalid name " + id + "!");
			if(!id.matches(REGNAME_REGEX)) // has only letters and digits and starts with a letter?
				throw new InvalidCPUException("Invalid name " + id + "!");
			if(cpu.hasRegister(REGISTER_PREFIX + id)) 
				throw new InvalidCPUException("Invalid name " + id + "!");
				
			cpu.registerNames.add(id);
		}
	}
}