package cross.language.algorithm

import java.util.*


class HungarianAlgorithm {
    var n = 0
    lateinit var cost: Array<IntArray> //cost matrix
    var max_match = 0 //n workers and n jobs
    lateinit var lx: IntArray
    lateinit var ly: IntArray //labels of X and Y parts
    lateinit var xy: IntArray //xy[x] - vertex that is matched with x,
    lateinit var yx: IntArray //yx[y] - vertex that is matched with y
    lateinit var S: BooleanArray
    lateinit var T: BooleanArray //sets S and T in algorithm
    lateinit var slack: IntArray //as in the algorithm description
    lateinit var slackx: IntArray //slackx[y] such a vertex, that
    lateinit var prev_ious: IntArray //array for memorizing alternating p

    var final_cost: Int = 0
    var final_assignment: MutableList<Int> = mutableListOf()

    fun init_labels() {
        Arrays.fill(lx, 0)
        Arrays.fill(ly, 0)
        for (x in 0 until n) for (y in 0 until n) lx[x] = Math.max(
            lx[x],
            cost[x][y]
        )
    }

    fun update_labels() {
        var x: Int
        var y: Int
        var delta = 99999999 //init delta as infinity
        y = 0
        while (y < n) {
            //calculate delta using slack
            if (!T[y]) delta = Math.min(delta, slack[y])
            y++
        }
        x = 0
        while (x < n) {
            //update X labels
            if (S[x]) lx[x] -= delta
            x++
        }
        y = 0
        while (y < n) {
            //update Y labels
            if (T[y]) ly[y] += delta
            y++
        }
        y = 0
        while (y < n) {
            //update slack array
            if (!T[y]) slack[y] -= delta
            y++
        }
    }

    fun add_to_tree(
        x: Int,
        prev_iousx: Int
    ) //x - current vertex,prev_iousx - vertex from X before x in the alternating path,
    //so we add edges (prev_iousx, xy[x]), (xy[x], x)
    {
        S[x] = true //add x to S
        prev_ious[x] = prev_iousx //we need this when augmenting
        for (y in 0 until n)  //update slacks, because we add new vertex to S
            if (lx[x] + ly[y] - cost[x][y] < slack[y]) {
                slack[y] = lx[x] + ly[y] - cost[x][y]
                slackx[y] = x
            }
    }

    fun augment() //main function of the algorithm
    {
        if (max_match == n) return  //check whether matching is already perfect
        var x: Int
        var y: Int //just counters and root vertex
        val q = IntArray(n)
        var wr = 0
        var rd = 0 //q - queue for bfs, wr,rd - write and read
        //pos in queue
        Arrays.fill(S, false) //init set S
        Arrays.fill(T, false) //init set T
        Arrays.fill(prev_ious, -1) //init set prev_ious - for the alternating tree
        var root = -1
        x = 0
        while (x < n) {
            if (xy[x] == -1) {
                root = x
                q[wr++] = root
                prev_ious[x] = -2
                S[x] = true
                break
            }
            x++
        }
        if (root == -1) {
            // All vertices are already matched
            return
        }
        y = 0
        while (y < n) {
            slack[y] = lx[root] + ly[y] - cost[root][y]
            slackx[y] = root
            y++
        }

        //second part of augment() function
        while (true) //main cycle
        {
            while (rd < wr) //building tree with bfs cycle
            {
                x = q[rd++] //current vertex from X part
                y = 0
                while (y < n) {
                    //iterate through all edges in equality graph
                    if (cost[x][y] == lx[x] + ly[y] && !T[y]) {
                        if (yx[y] == -1) break //an exposed vertex in Y found, so
                        //augmenting path exists!
                        T[y] = true //else just add y to T,
                        q[wr++] = yx[y] //add vertex yx[y], which is matched
                        //with y, to the queue
                        add_to_tree(yx[y], x) //add edges (x,y) and (y,yx[y]) to the tree
                    }
                    y++
                }
                if (y < n) break //augmenting path found!
            }
            if (y < n) break //augmenting path found!
            update_labels() //augmenting path not found, so improve labeling
            rd = 0
            wr = rd
            y = 0
            while (y < n) {
                //in this cycle we add edges that were added to the equality graph as a
                //result of improving the labeling, we add edge (slackx[y], y) to the tree if
                //and only if !T[y] && slack[y] == 0, also with this edge we add another one
                //(y, yx[y]) or augment the matching, if y was exposed
                if (!T[y] && slack[y] == 0) {
                    if (yx[y] == -1) //exposed vertex in Y found - augmenting path exists!
                    {
                        x = slackx[y]
                        break
                    } else {
                        T[y] = true //else just add y to T,
                        if (!S[yx[y]]) {
                            q[wr++] = yx[y] //add vertex yx[y], which is matched with
                            //y, to the queue
                            add_to_tree(yx[y], slackx[y]) //and add edges (x,y) and (y,
                            //yx[y]) to the tree
                        }
                    }
                }
                y++
            }
            if (y < n) break //augmenting path found!
        }
        if (y < n) //we found augmenting path!
        {
            max_match++ //increment matching
            //in this cycle we inverse edges along augmenting path
            var cx = x
            var cy = y
            var ty: Int
            while (cx != -2) {
                ty = xy[cx]
                yx[cy] = cx
                xy[cx] = cy
                cx = prev_ious[cx]
                cy = ty
            }
            augment() //recall function, go to step 1 of the algorithm
        }
    } //end of augment() function

    fun solve(Arr: IntArray, N: Int): HungarianAlgorithm {
        // init collections
        n = N
        cost = Array(n) { IntArray(n) }
        lx = IntArray(n)
        ly = IntArray(n)
        S = BooleanArray(n)
        T = BooleanArray(n)
        slack = IntArray(n)
        slackx = IntArray(n)
        prev_ious = IntArray(n)
        for (i in 0 until n) for (j in 0 until n) cost[i][j] =
            -1 * Arr[i * n + j]

        // hungarian algorithm
        max_match = 0 //number of vertices in current matching
        xy = IntArray(n)
        yx = IntArray(n)
        Arrays.fill(xy, -1)
        Arrays.fill(yx, -1)
        init_labels() //step 0
        augment() //steps 1-3

        // calculate result
        final_assignment.clear()    // optimal matching
        final_cost = 0      //weight of the optimal matching
        for (x in 0 until n) {//forming answer there
            final_assignment.add(x, xy[x])
            final_cost += cost[x][xy[x]]
        }
        final_cost *= -1

        return this
    }

}
