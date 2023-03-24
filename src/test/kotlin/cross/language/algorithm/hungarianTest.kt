package cross.language.algorithm

fun main(args: Array<String>) {
    val n = 3
    val Arr = intArrayOf(1500, 4000, 4500, 2000, 6000, 3500, 2000, 4000, 2500) /*1500 4000 4500
																	2000 6000 3500
																	2000 4000 2500*/
    val ob = HungarianAlgorithm()
    println(ob.assignmentProblem(Arr, n))
}