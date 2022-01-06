After Extrating zip folder place shkc folder in java-pommerman-master\src\players 
for creating a player in Run.java use the following constructor
     players.shkc.Parametersshkc params = new players.shkc.Parametersshkc();
     params.stop_type = params.STOP_ITERATIONS;
     params.num_iterations = 200;
     params.rollout_depth = 10;
     params.policyType = policy;
     params.AMAFPolicyEnable = AMAFPolicyEnable;
     p = new Playershkc(seed, playerID++, params);


The package Shkc consists of 4 java classes:

playershkc.java

parametersshkc.java

Heuristicsshkc.java
  it contains all the heuristics logic functions.

SingletreeNodeshkc.java

this file contains different tree policy calls
to use the desired tree policy uncomment/comment the following lines from 199 to 123 in this SingletreeNodeshkc.java class

//                curr = curr.uct(state);
                  curr = curr.uct2Tuned(state);
//                curr = curr.uctBayesian(state);
//                curr = curr.AMAF(state);
//                curr = curr.alphaAMAF(state, params.AMAPAplha);


To run a player in Test.java use the following line
players.add(new players.shkc.Playershkc(seed, playerID));


