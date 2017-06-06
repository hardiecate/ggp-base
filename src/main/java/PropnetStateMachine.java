import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class PropnetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
            roles = propNet.getRoles();
            ordering = getOrdering();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * for compound games with independent subgames
     * where actions are disjunctive, termination
     * and goals are in one factor.
     */
    public void factorIndependentSubgames() {
    	/* pseudocode for logic
    	Proposition t = termination proposition
    	List<Proposition> relevant = new ArrayList<Proposition> ();
    	for (Proposition index: indices) {
    		if (termination.source.contains(index)) {
    			relevant.add(index);
    		}
    	}

    	return relevant // ??? Do we want this to return or just straight up replace the indices right here and now?
    	*/
    }

    /**
     * for compound games where performance in one "best"
     * subgame determines score for overall game
     */
    public void factorDisjunctive() {
		/* pseudocode for logic

		connective = connective leading to termination

		while (termination only has disjunctions)
			if (termination has non-disjunction or  flag) break;
			if (connective instance of or &&
				inputs are supplied by nodes in different subgames) {
				cut off that node and inputs to or gate termination nodes for overall game
			}
		}

		pick one subgame and proceed as just that game
		 */

    	/* possible extensions to strengthen
    	1. check each subgame for termination when no action is played
    	2. take shortest time period
    	3. play each of other subgames with that as step limit
    	 */
    }

    /**
     * for compound games where action in one subgame affects
     * all other subgames
     */
    public void factorConjunctive() {
    	/* pseudocode for logic
    	//group actions into equivalence classes of actions
    	if outputs of two actions are input to & gate with same
    	other input OR
    	if output of two actions go to OR gate,
    	then they are equivalent.

    	//determine if classes satisfy lossless join group
    	if each equiv class has one subgame where it has nonempty intersection
    	of each equivalent class of every other subgame,
    	then it has lossless join property

    	//if equivalent and lossless join
    	factor game into subgames
    	modify propnet s.t. individual actions are changed to
    	equivalent classes of which the actions are members
    	 */
    }

    public void clearPropNet() {
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(false);
        }
        for (Proposition p : propNet.getInputPropositions().values()) {
        		p.setValue(false);
        }
        propNet.getInitProposition().setValue(false);


//        for (Proposition p : propNet.getLegalPropositions().values()) {
//
//        }

    }

    public void markActions(List<Move> moves, MachineState state) {
//    	System.out.println("Now in markActions");
//    	System.out.println(moves.toString());
    	Set<GdlSentence> contents = state.getContents();
    	Map<GdlSentence, Proposition> inputProps = propNet.getInputPropositions();
    	for (Proposition p : inputProps.values()) {
//    		System.out.println("Marking an input prop");
    		if (moves.contains(getMoveFromProposition(p))) {
        		p.setValue(true);
    		}
    	}
    }

    public void markBases(MachineState state) {
    	Set<GdlSentence> contents = state.getContents();
    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();
    	for (GdlSentence sen : baseProps.keySet()) {
    		if (contents.contains(sen)) {
        		baseProps.get(sen).setValue(true);
    		}
    	}
    }

    public boolean conjunction (Component p) {
    	List<Component> inputs = new ArrayList<Component>(p.getInputs());
    	int len = inputs.size();
    	for (int i = 0; i < len; i++) {
    		if (!(propMarkP(inputs.get(i)))) {
    			return false;
    		}
    	}
    	return true;
    }

    public boolean disjunction (Component p) {
    	List<Component> inputs = new ArrayList<Component>(p.getInputs());
    	int len = inputs.size();
    	for (int i = 0; i < len; i++) {
    		if (propMarkP(inputs.get(i))) {
    			return true;
    		}
    	}
    	return false;
    }

    public boolean negation (Component p) {
    	return (!(propMarkP(p.getSingleInput())));
    }

    public boolean propMarkP(Component p) {
    	if (p.getInputs().size() == 0) {
//    		System.out.println("This is an input proposition");
    		return p.getValue();
    	} else {
    		if (p instanceof And) {
    			// Conjunction
//    			System.out.println("This is a conjunction propositoin");
    			return conjunction(p);
    		} else if (p instanceof Or) {
//    			System.out.println("This is a disjunction proposition");
    			return disjunction(p);
    		} else if (p instanceof Not) {
//    			System.out.println("This is a negation proposition");
    			return negation(p);
    		}


    		List<Component> inputs = new ArrayList<Component>(p.getInputs());

    		if (p.getInputs().size() == 1 && inputs.get(0) instanceof Transition) {
    			// This is a base proposition
//    			System.out.println("This is a base proposition.");
    			return p.getValue();
    		} else {
//    			System.out.println("This is a view proposition.");
    			return propMarkP(p.getSingleInput());
    		}
    	}
    }

    // I believe this is implemented correctly but I'm not sure where it's used
    public GdlSentence propreward (Role role, MachineState state) {
    	clearPropNet();
        markBases(state);
        List<Proposition> rewards;
        for (int i=0; i<roles.size(); i++) {
            if (role==roles.get(i)) {
                //treating goal propositions as rewards?
                Map<Role, Set<Proposition>> goals = propNet.getGoalPropositions();
                rewards = new ArrayList<Proposition> (goals.get(roles.get(i)));
                for (int j = 0; j < rewards.size(); j++) {
                    if (propMarkP(rewards.get(j))) {
                        return rewards.get(j).getName();
                    }
                }
                break;
            }
        }
//        for (int i = 0; i < rewards.size(); i++) {
//            if (propMarkP(rewards.get(i))) {
//                return rewards.get(i).getName();
//            }
//        }
        return null;
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
    	clearPropNet();
    	markBases(state);
    	Proposition termProp = propNet.getTerminalProposition();
        return propMarkP(termProp);
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
//    	System.out.println("This state is: " + state.toString());
    	clearPropNet();
    	markBases(state);
    	Set<GdlSentence> contents = state.getContents();
        Map<Role, Set<Proposition>> goals = propNet.getGoalPropositions();
        Set<Proposition> myGoals = goals.get(role);

//        System.out.println(myGoals.size());
        for (Proposition goal: myGoals) {
//    		System.out.println(goal.toString());
//    		System.out.println(getGoalValue(goal));
//    		System.out.println();
        	if (propMarkP(goal)) {
        		return getGoalValue(goal);
        	}
//        	return getGoalValue(goal);
        }
        return 0;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
    	clearPropNet();
    	propNet.getInitProposition().setValue(true);
    	return getStateFromBase();
    }

    /**
     * Computes all possible actions for role.
     * I don't believe we use this anywhere?
     * Do we still have to write it?
     */
    @Override
    public List<Move> findActions(Role role)
            throws MoveDefinitionException {
    	//TODO: FIND ACTIONS

        // Set of legal input propositions for this role
    	Map<GdlSentence, Proposition> moves = new HashMap<GdlSentence, Proposition> (propNet.getInputPropositions());
        List<Move> toReturn = new ArrayList<Move>();
        // Propogate input propositions, add them to list of moves if they propogate to true
        int len = moves.size();
        for (int i = 0; i < len; i++) {
        	toReturn.add(getMoveFromProposition(moves.get(i)));
        }

        return toReturn;
    }


    @Override
	protected void crossProductLegalMoves(List<List<Move>> legals, List<List<Move>> crossProduct, LinkedList<Move> partial)
    {
        if (partial.size() == legals.size()) {
            crossProduct.add(new ArrayList<Move>(partial));
        } else {
            for (Move move : legals.get(partial.size())) {
                partial.addLast(move);
                crossProductLegalMoves(legals, crossProduct, partial);
                partial.removeLast();
            }
        }
    }







    @Override
    public List<List<Move>> getLegalJointMoves(MachineState state) throws MoveDefinitionException {
//    	System.out.println("In getLegalJointMoves");
//    	List<List<Move>> eachLegalMoves = new ArrayList<List<Move>>();
//    	for (Role role: roles) {
//    		List<Move> legalMoves = getLegalMoves(state, role);
//			eachLegalMoves.add(legalMoves);
//    	}
//    	System.out.println("eachLegalMoves");
//    	System.out.println(eachLegalMoves);
//    	List<List<Move>> result = new ArrayList<List<Move>>();
//    	for (List<Move> roleMoves: eachLegalMoves) {
//	    	List<List<Move>> newResult = new ArrayList<List<Move>>();
//    		for (Move move: roleMoves) {
//    	    	for (List<Move> resultContent: result) {
//    	    		List<Move> newContent = resultContent;
//    	    		newContent.add(move);
//    	    		newResult.add(newContent);
//    	    	}
//    		}
//	    	result = newResult;
//    	}
//    	System.out.println("result printed");
//    	System.out.println(result);
//    	return result;

//    	System.out.println("Now in getLegalJointMoves!");

        List<List<Move>> legals = new ArrayList<List<Move>>();
        for (Role role : getRoles()) {
            legals.add(getLegalMoves(state, role));
        }

        List<List<Move>> crossProduct = new ArrayList<List<Move>>();
        crossProductLegalMoves(legals, crossProduct, new LinkedList<Move>());

        return crossProduct;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {

        clearPropNet();
        markBases(state);

        // Set of legal input propositions for this role
        List<Proposition> legals = new ArrayList<Proposition> (propNet.getLegalPropositions().get(role));
        List<Move> actions = new ArrayList<Move>();

        // Propogate input propositions, add them to list of moves if they propogate to true
        int len = legals.size();
        for (int i = 0; i < len; i++) {
        	if (propMarkP(legals.get(i))) {
        		actions.add(getMoveFromProposition(legals.get(i)));
        	}
        }

        return actions;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
    	clearPropNet();
        markActions(moves, state);
        markBases(state);


//        MachineState newstate = state;
//
//    	Map<GdlSentence, Proposition> baseProps = propNet.getBasePropositions();
//    	for (Proposition p : baseProps.values()) {
////    		if (propMarkP(p.getSingleInput())) {
//        		p.setValue(propMarkP(p.getSingleInput()));
////    		}
//    	}



        return getStateFromBase();
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        // TODO: Compute the topological ordering.

        return order;
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

    /* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
//            p.setValue(p.getSingleInput().getValue());
            if (propMarkP(p.getSingleInput()))
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }

}