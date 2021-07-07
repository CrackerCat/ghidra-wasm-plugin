package wasm.analysis;

import java.util.ArrayList;

import ghidra.program.model.address.Address;

public class WasmFunctionAnalysisState {

	private ArrayList<MetaInstruction> metas = new ArrayList<>();
	
	public void collectMeta(MetaInstruction meta) {
		metas.add(meta);
	}
	
	@Override
	public String toString() {
		return metas.toString();
	}
	
	public MetaInstruction findMetaInstruction(Address a, MetaInstruction.Type type) {
		for(MetaInstruction instr: metas) {
			if(instr.location.equals(a) && instr.getType() == type) {
				return instr;
			}
		}
		return null;
	}

	public void performResolution() {
		ArrayList<MetaInstruction> controlStack = new ArrayList<>();
		int valueStackDepth = 0; //number of items on the value stack
		
		for(MetaInstruction instr: this.metas) {
			switch(instr.getType()) {
			case PUSH:
				valueStackDepth++;
				break;
			case POP:
				valueStackDepth--;
				break;
			case BEGIN_LOOP:
				BeginLoopMetaInstruction beginLoop = (BeginLoopMetaInstruction) instr;
				beginLoop.stackDepthAtStart = valueStackDepth;
				controlStack.add(beginLoop);
				break;
			case BEGIN_BLOCK:
				controlStack.add(instr);
				break;
			case BR:
				BrMetaInstruction br = (BrMetaInstruction)instr;
				MetaInstruction target = controlStack.get(controlStack.size() - 1 - br.level);
				switch(target.getType()) {
				case BEGIN_BLOCK:
				case IF:
					//jump to the end of the corresponding block
					br.target = target.getEndAddress();
					br.implicitPops = 0;
					break;
				case BEGIN_LOOP:
					//jump back to the beginning of the loop and pop everything that's been pushed since the start
					br.target = target.location;
					BeginLoopMetaInstruction loop = (BeginLoopMetaInstruction)target;
					br.implicitPops = valueStackDepth - loop.stackDepthAtStart;
					break;
				default:
					throw new RuntimeException("Invalid item on control stack " + target);
				}
				break;
			case ELSE:
				IfMetaInstruction ifStmt = (IfMetaInstruction) controlStack.get(controlStack.size() - 1);
				ElseMetaInstruction elseStmt = (ElseMetaInstruction)instr;
				ifStmt.elseInstr = elseStmt;
				elseStmt.ifInstr = ifStmt;
				break;
			case END:
				MetaInstruction begin = controlStack.remove(controlStack.size() - 1);
				switch(begin.getType()) {
				case BEGIN_BLOCK:
					BeginBlockMetaInstruction beginBlock = (BeginBlockMetaInstruction)begin;
					beginBlock.endLocation = instr.location;
					break;
				case IF:
					IfMetaInstruction ifInstr = (IfMetaInstruction)begin;
					ifInstr.endLocation = instr.location;
					break;
				case BEGIN_LOOP:
					BeginLoopMetaInstruction loop = (BeginLoopMetaInstruction)begin;
					loop.endLocation = instr.location;
					break;
				default:
					throw new RuntimeException("Invalid item on control stack " + begin);
				}
				break;
			case IF:
				controlStack.add(instr);
				break;
			case RETURN:
				if(valueStackDepth != 0) {
					if(valueStackDepth != 1) {
						throw new RuntimeException("Too many items on stack at return");
					}
					ReturnMetaInstruction ret = (ReturnMetaInstruction) instr;
					ret.returnsVal = true;
					valueStackDepth--;
				}
				break;
			}
		}
	}
}