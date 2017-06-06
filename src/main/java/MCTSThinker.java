import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ggp.base.apps.player.Player;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

// Makes a fixed depth without heuristics move

public class MCTSThinker extends StateMachineGamer {

	Player p;
	int limit = 1;
	double w1 = .6;
	double w2 = .4;
	double last_player_score = 0;
	boolean restrict = true;
	int timeoutPadding = 2000;
	boolean wasTimedOut = false;
	int mcsCount = 100;
	long returnBy;
	Move bestSoFar = null;
	StateMachine machine = null;
	Role myRole = null;
	Map<MachineState, Integer> visited = new HashMap<MachineState, Integer>();
	Map<MachineState, Integer> utility = new HashMap<MachineState, Integer>();
	Map<MachineState, List<MachineState>> parents = new HashMap<MachineState, List<MachineState>>();
	Map<MachineState, List<MachineState>> children = new HashMap<MachineState, List<MachineState>>();
	int depthchargeCount;

	@Override
	public StateMachine getInitialStateMachine() {
//				return new CachedStateMachine(new ProverStateMachine());

		return new PropnetStateMachine(); // changed to propnet machine
	}


	// This is where the pre-game calculations are done.
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		depthchargeCount = 0;
		machine = getStateMachine();
		machine.initialize(getMatch().getGame().getRules());
		myRole = getRole();
		machine.getInitialState();
		//		ProverStateMachine machine2 = new ProverStateMachine();
		//		machine2.initialize(getMatch().getGame().getRules());
		//
		//
		//		StateMachineVerifier.checkMachineConsistency(machine2, machine, 20000);
	}


	public Move chooseMove(List<Integer> possibleScores, List<Move> possibleMoves) {

		// Choosing the move that correlates to the highest montecarlo score
		Move bestChoice = null;
		int highestScore = 0;
		int numMoves = possibleMoves.size();
		System.out.println("We had " + numMoves + " moves available.");
		System.out.println(possibleScores.toString());
		System.out.println();
		for (int i = 0; i < numMoves; i++) {
			if (possibleScores.get(i) >= highestScore) {
				highestScore = possibleScores.get(i);
				bestChoice = possibleMoves.get(i);
			}
		}

		System.out.println("Depth charge count: " + depthchargeCount);
		depthchargeCount = 0;
		return bestChoice;
	}

	private boolean timeLeft(int calculationTime){
		if (returnBy - calculationTime > System.currentTimeMillis()) {return true;}
		return false;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		depthchargeCount = 0;
		wasTimedOut = false;
		System.out.println(limit);

		int calculationTime = 1000;
		returnBy = timeout - timeoutPadding;


		MachineState stateOrigin = getCurrentState();
		long start = System.currentTimeMillis();
		List<Move> possibleMoves = machine.getLegalMoves(stateOrigin, myRole);
		// Creates a list of the same size as possibleMoves, all populated with 0s
		List<Integer> possibleScores = new ArrayList<Integer>((Collections.nCopies(possibleMoves.size(), 0)));
		int numMoves = possibleMoves.size();
		if (numMoves == 1) {
			System.out.println("There was only one move to make!");
			return possibleMoves.get(0);

		}

		//Is this enough padding for our calculations?
		while (timeLeft(calculationTime)) {
			//STEPS ONE & TWO:
			//Do selection routine to find our unvisited starting point
			//Also within this method, add successors to the tree
			MachineState state = select(stateOrigin);
			if (!expand(state)) continue;

			// Setting the possible scores
			for (int i = 0; i < numMoves; i++) {
				//				System.out.println("Exploring move number: " + i);
				if (!(timeLeft(calculationTime))) {
					return chooseMove(possibleScores, possibleMoves);
				}

				//STEP THREE:
				//Same simulation routine w/ random action choices until
				//terminal state reached
				Random randomizer = new Random();
				Move aMove = possibleMoves.get(i);
				List<List<Move>> rounds = machine.getLegalJointMoves(state, myRole, aMove);
				int random = randomizer.nextInt(rounds.size());
				List<Move> randomRound = rounds.get(random);
				MachineState newstate = machine.getNextState(state, randomRound);
				int score = montecarlo(myRole, newstate, machine);
				possibleScores.set(i, score);

				//STEP FOUR:
				//Propogate back the value of the terminal state reached by the
				//depth charge within montecarlo
				if(timeLeft(calculationTime)) {
					backpropogate(state, score);
				}
			}
		}

		System.out.println("Depth charge count: " + depthchargeCount);
		visited.clear();
		parents.clear();
		return chooseMove(possibleScores, possibleMoves);

	}

	public int oppoProximity(List<Role> opponents, MachineState state, StateMachine machine) throws GoalDefinitionException {
		double score = 0.0;
		for (Role opponent: opponents) {
			score += machine.getGoal(state, opponent);
		}
		return 100 - (int)(score / opponents.size());
	}


	public List<Role> findOpponents(Role role, StateMachine machine) {
		List<Role> allRoles = machine.getRoles();
		List<Role> opponents = new ArrayList<Role>();
		for (Role testRole: allRoles) {
			if (testRole != role) opponents.add(testRole);
		}
		return opponents;
	}

	public int evalfn(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException, GoalDefinitionException {
		//return 0; // for non heuristics
		double player = (double)machine.getGoal(state, role); // for simple goal proximity
		double opp = (double)oppoProximity(findOpponents(role, machine), state, machine);
		double f1 = (double)mobility(role, state, machine); // for mobility heuristic
		double f2 = (double)focus(role, state, machine); // for focus heuristic

		//update strategy based on whether your score is going up or down from last move
		//if the opponent's score is higher than our player's score
		if (opp > player) {
			//if our score went down from the last round
			if (player < last_player_score) {
				System.out.println("Switching strategies!");;
				restrict = !restrict;
				if (restrict) {
					w2 = w1; // Not sure why we kept cutting this factor in half
					w1 = 1.0 - w2;
				} else {
					w1 = w2;
					w2 = 1.0 - w1;
				}
			}
			last_player_score = player;
		}

		// If we are really close to a goal state, emphasize that over the mobility/focus heuristics
		if (player > (w1*f1 + w2*f2)) {
			System.out.println("We are just gonna go for the goal.");
		}
		return (int)Math.max(player, (w1*f1 + w2*f2)); //(int)(w1*f1 + w2*f2);
	}

	public int selectfn(MachineState node, MachineState parent) throws MoveDefinitionException, GoalDefinitionException {
		int result = (int)((1.0*utility.get(node)/(1.0*visited.get(node))) + Math.sqrt(2*Math.log(visited.get(parent))))/(visited.get(node));
		System.out.println("Utility is: " + utility.get(node));
		System.out.println("Visit count is: " + visited.get(node));
		System.out.println("Parent visit count is: " + visited.get(parent));
		System.out.println();
		return (int)((1.0*utility.get(node)/(1.0*visited.get(node))) + Math.sqrt(2*Math.log(visited.get(parent))))/(visited.get(node));
	}

	public MachineState select(MachineState node) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (!(visited.containsKey(node))) {return node;}
		List<MachineState> myChildren = children.get(node);

		// Update the parents list
		for (MachineState child : myChildren) {
			if (parents.containsKey(child)) {
				if (!(parents.get(child).contains(node))) {
					List<MachineState> pars = parents.get(child);
					pars.add(node); // Updating list of this child's parents
					parents.put(node, pars); // Updating the global map

				}
			} else {
				List<MachineState> pars = new ArrayList<MachineState>();
				pars.add(node); // Updating list of this child's parents
				parents.put(node, pars); // Updating the global map

			}
		}

		// Return first child that has not been visited yet
		for (MachineState child : myChildren) {
			if (!(visited.containsKey(child))) {
				return child;
			}
		}
		int score = Integer.MIN_VALUE;
		MachineState result = node;

		// All children have been visited. Find highest score among children.
		for (MachineState child: myChildren) {
			int newscore = selectfn(child, node);
			if (newscore>score) {
				score = newscore;
				result=child;
			}
		}
		return select(result);
	}

		public boolean expand (MachineState node) throws MoveDefinitionException, TransitionDefinitionException {
			if (machine.isTerminal(node)) {
				if (children.containsKey(node)) {
					children.remove(node);

				}
				//			System.out.println("reached a terminal state, about to return: " + machine.getGoal(state,  role));
				return false;
			}

			List<Move> actions = machine.getLegalMoves(node, myRole);
			for (Move action: actions) {
				List<List<Move>> moves = machine.getLegalJointMoves(node, myRole, action);
//				moves.add(action);
				for (List<Move> scenario : moves) {
					MachineState newstate = machine.getNextState(node, scenario);
					if (children.containsKey(node)) {
						List<MachineState> childs = children.get(node);
						childs.add(newstate);
						children.put(node, childs);
					} else {
						List<MachineState> childs = new ArrayList<MachineState> ();
						childs.add(newstate);
						children.put(node, childs);
					}
				}

			}
			return true;
		}

	public boolean backpropogate(MachineState node,  int score) {
		if (visited.containsKey(node)) {
			visited.put(node, visited.get(node) + 1);
			utility.put(node, utility.get(node) + score);
		} else {
			visited.put(node,  1);
			utility.put(node, score);
		}
		List<MachineState> pars = parents.get(node);
		if (pars != null) {
			for (MachineState parent : pars) {
				if (!(timeLeft(1000))) {
					return true;
				}
				backpropogate(parent, score);
			}
		}

		return true;
}

public int montecarlo(Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
	if (machine.isTerminal(state)) {
		System.out.println("montecarlo was given a terminal state");
		//			System.out.println("reached a terminal state, about to return: " + machine.getGoal(state,  role));
		return machine.getGoal(state, role);
	}


	int total = 0;

	for (int i = 0; i < mcsCount; i++) {
		int charge = depthcharge(role, state, machine);
		total = total + charge;
	}
	return (int) (1.0*total/mcsCount);

}

public int depthcharge (Role role, MachineState state, StateMachine machine) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {


	if (machine.isTerminal(state)) {
		depthchargeCount++;
		System.out.println("Terminal state:");
		System.out.println("Returning: " + machine.getGoal(state, role));
		//			System.out.println("reached a terminal state, about to return: " + machine.getGoal(state,  role));
		return machine.getGoal(state, role);
	}

	//	    if (System.currentTimeMillis() >= returnBy) {
	//	    	wasTimedOut = true;
	//	    	System.out.println("We timed out!");
	//	        return 0;
	//	    }

	if (!(timeLeft(1000))) {
		//			System.out.println("ran out of time, about to return: 0");
		wasTimedOut = true;
		//			return evalfn(role, state, machine);
		return 0;
	}

	Random randomizer = new Random();
	//		System.out.println("right before getlegaljointmoves");
	List<List<Move>> legalJointMoves = machine.getLegalJointMoves(state);
	int random = randomizer.nextInt(legalJointMoves.size());
	List<Move> randomRound = legalJointMoves.get(random);
	MachineState newstate = machine.getNextState(state, randomRound);
//	System.out.println("Old state: " + state.toString());
//	System.out.println("New state: " + newstate.toString());
//	System.out.println();

	return depthcharge(role, newstate, machine);
}


public int mobility(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException {
	List<Move> actions = machine.getLegalMoves(state, role);

	List<Move> feasibles = machine.findActions(role);
	int mobility = (int)((double)actions.size()/(double)feasibles.size() * 100);
	//		System.out.println(mobility);
	return mobility;
}

public int focus(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException {
	return 100 - mobility(role, state, machine);
}


@Override
public void stateMachineStop() {
	// TODO Auto-generated method stub

}

@Override
public void stateMachineAbort() {
	// TODO Auto-generated method stub

}

@Override
public void preview(Game g, long timeout) throws GamePreviewException {
	// TODO Auto-generated method stub

}

@Override
public String getName() {
	// TODO Auto-generated method stub
	return "MCTSThinker";
}

//
//public GdlRule prunesubgoals(GdlRule rule) throws SymbolFormatException {
//	List<GdlLiteral> vl = new ArrayList<GdlLiteral>();
//	vl.add(rule.get(0));
//	List<GdlLiteral> newrule = new ArrayList<GdlLiteral>();
//	newrule.add(rule.get(0));
//	for (int i = 2; i < rule.arity(); i++) {
//		List<GdlLiteral> sl = newrule;
//		for (int x = i + 1; x < rule.arity(); x++) {
//			sl.add(rule.get(x));
//		}
//		GdlRule arg1 = GdlPool.getRule(GdlFactory.createTerm("rule").toSentence(), sl);
//		GdlLiteral arg2 = rule.get(i);
//		GdlRule arg3 = GdlPool.getRule(GdlFactory.createTerm("rule").toSentence(), vl);
//	    if (!pruneworthyp(arg1, arg2, arg3)) {
//	    	newrule.add(rule.get(i));
//		}
//	};
//	GdlRule result = GdlPool.getRule(GdlFactory.createTerm("rule").toSentence(), newrule);
//	return result;
//	}
//
//public boolean pruneworthyp (GdlRule sl, GdlLiteral p, GdlRule vl) {
//	vl = varsexp(sl, vl.getBody());
//	HashMap<GdlLiteral, String> al = new HashMap<GdlLiteral, String>();
//	for (int i=0; i < vl.arity(); i++) {
//		Integer x = i;
//		al.put(vl.get(i), "x" + x.toString());
//		// but vl is just one variable long.. see prunesubgoals
//	}
//	GdlRule facts = sublis(sl,al);
//	GdlRule goal = sublis(p,al); // how are we putting p in as well when p and sl have different types?
//	return compfindp(goal,facts);
//}
//
//public boolean compfindp(GdlRule goal, GdlRule facts) {
//	for (int i = 0; i < facts.arity(); i++) {
//		if (goal.get(0) == facts.get(i)) return true;
//	}
//	return false;
//}
//
//public GdlRule sublis(GdlRule a, GdlRule b) throws SymbolFormatException {
//	List<GdlLiteral> c = new ArrayList<GdlLiteral>();
//	for (int i = 0; i < a.arity(); i++) {
//		// detect first variable in a
//		// save the rule structure of a[i]
//		// and variable of b[i]
//		// at c[i]
//	}
//	GdlRule result = GdlPool.getRule(GdlFactory.createTerm("rule").toSentence(), c);
//	return result;
//}
//
//
//
//
//
//
//public List<GdlRule> prunerules (List<Gdl> list) {
//	List<GdlRule> rules = new ArrayList<GdlRule>();
//
//
//	for (Gdl g : list) {
//		if (g.toString().indexOf("( <= (") == 0) {
//			rules.add((GdlRule) g);
//		}
//	}
//
//
//	List<GdlRule> newRules = new ArrayList<GdlRule>();
//	for (int i = 0; i < rules.size(); i++) {
//
//		if (!subsumedp(rules.get(i), newRules) && (!(subsumedp(rules.get(i), rules.subList(i+1, rules.size()))))) {
//			newRules.add(rules.get(i));
//		}
//	}
//	return newRules;
//}
//
//public boolean subsumedp (GdlRule rule, List<GdlRule> rules) {
//	for (int i = 0; i < rules.size(); i++) {
//		if (subsumesp(rules.get(i), rule)) {
//			return true;
//		}
//	}
//	return false;
//}
//
//public boolean subsumesp (GdlRule pRule, GdlRule qRule) {
//
//	List<GdlLiteral> p = pRule.getBody();
//	List<GdlLiteral> q = qRule.getBody();
//	System.out.println(q.toString());
//	System.out.println(p.toString());
//	System.out.println();
//
//	if (p.equals(q)) {
//		return true;
//	}
//
//	// if (symbolp(p) || symbolp(q)) {
//	//	return false;
//	// }
//
//	for (GdlLiteral pLit : p) {
//		for (GdlLiteral qLit : q) {
//			Map<String, String> al = matcher(pLit, qLit);
//			if (al != null && subsumesexp(p.subList(1, p.size()), q.subList(1, q.size()), al)) {
//				return true;
//			}
//		}
//	}
//
//	return false;
//}
//
//
//public boolean subsumesexp (List<GdlLiteral> pl, List<GdlLiteral> QL, Map<String, String> AL) {
//	if (pl.size() == 0) {
//		return true;
//	}
//	for (int i = 0; i < QL.size(); i++) {
//		Map<String, String> bl = matcher(pl.get(0), QL.get(i)/*, AL*/);
//		if (bl != null && subsumesexp(pl.subList(1, pl.size()), QL, bl)) {
//			return true;
//		}
//	}
//	return false;
//}
//
//public Map<String, String> matcher (GdlLiteral p, GdlLiteral q) {
//	System.out.println("Now in matcher");
//
//	Map<String, String> toReturn = new HashMap<String, String>();
//	String[] pString = p.toString().split(" ");
//	String[] qString = q.toString().split(" ");
//	System.out.println("Literal string for p: ");
//	for (String s : pString) {
//		if (s.contains("?")) {
//			System.out.println(s);
//		}
//	}
//	System.out.println("Literal string for q: ");
//	for (String s : qString) {
//		if (s.contains("?")) {
//			System.out.println(s);
//		}
//	}
//	System.out.println();
//
//
//	if (toReturn.size() == 0) {
//		return null;
//	}
//	return toReturn;
//}

}