import tornadofx.*
import kotlin.math.exp

enum class SearchStrategy {
    RANDOM {
        override fun execute() {
            val capturedCities = mutableSetOf<Int>()

            val startingEdge = Edge.all.sample()
            var edge = startingEdge

            while (capturedCities.size < City.all.size) {
                capturedCities += edge.startCity.id

                val nextRandom = Edge.all.asSequence()
                        .filter { it.startCity.id !in capturedCities }
                        .sampleOrNull() ?: startingEdge

                edge.endCity = nextRandom.startCity
                edge = nextRandom
            }

            if (!Tour.isMaintained) throw Exception("Tour broken in RANDOM SearchStrategy \r\n${Edge.all.joinToString("\r\n")}")
            defaultAnimationOn = true
            println("Random: ${Tour.tourDistance}")
        }
    },

    REMOVE_OVERLAPS {
        override fun execute() {

            SearchStrategy.RANDOM.execute()
            defaultAnimationOn = false

            (1..10).forEach {
                Tour.conflicts.forEach { (x, y) ->
                    x.attemptTwoSwap(y)?.animate()
                }
            }

            defaultAnimationOn= true
            println("Remove Overlaps: ${Tour.tourDistance}")
        }
    },

    NEAREST_NEIGHBOUR {
        override fun execute() {
            val capturedCities = mutableSetOf<Int>()

            var edge = Edge.all.first()

            while (capturedCities.size < City.all.size) {
                capturedCities += edge.startCity.id

                val closest = Edge.all.asSequence().filter { it.startCity.id !in capturedCities }
                        .minByOrNull { edge.startCity.distanceTo(it.startCity) }
                        ?: Edge.all.first()

                edge.endCity = closest.startCity
                edge = closest
            }
            if (!Tour.isMaintained) throw Exception("Tour broken in NEAREST NEIGHBOUR SearchStrategy \r\n${Edge.all.joinToString("\r\n")}")
            defaultAnimationOn = true
            println("Nearest Neighbour: ${Tour.tourDistance}")
        }
    },

    TWO_OPT {
        override fun execute() {

            SearchStrategy.RANDOM.execute()
            defaultAnimationOn = false

            (1..10000).forEach { iteration ->
                Edge.all.sampleDistinct(2).toList()
                        .let { it.first() to it.last() }
                        .also { (e1,e2) ->

                            val oldDistance = Tour.tourDistance
                            e1.attemptTwoSwap(e2)?.also {
                                when {
                                    oldDistance <= Tour.tourDistance -> it.reverse()
                                    oldDistance > Tour.tourDistance -> it.animate()
                                }
                            }
                        }
            }

            if (!Tour.isMaintained) throw Exception("Tour broken in TWO_OPT SearchStrategy \r\n${Edge.all.joinToString("\r\n")}")
            defaultAnimationOn = true
            println("Two Opt: ${Tour.tourDistance}")
        }
    },

    SIMULATED_ANNEALING {
        override fun execute() {
            SearchStrategy.RANDOM.execute()
            defaultAnimationOn = false


            var bestDistance = Tour.tourDistance
            var bestSolution = Tour.toConfiguration()

             sequenceOf(
                    generateSequence(80.0) { (it - .05).takeIf { it >= 0 } }/*,
                    generateSequence(0.0) { (it + .005).takeIf { it <= 80 } },
                    generateSequence(80.0) { (it - .005).takeIf { it >= 30 } }*/
                    ).flatMap { it }
                     .plus(0.0)
                     .forEach { temp ->

                        Edge.all.sampleDistinct(2)
                                .toList()
                                .also { (e1,e2) ->

                                    val oldDistance = Tour.tourDistance

                                    // try to swap vertices on the two random edges
                                    val swap = e1.attemptTwoSwap(e2)

                                    // track changes in distance
                                    val newDistance = Tour.tourDistance

                                    //if a swap was possible
                                    if (swap != null) {

                                        // if swap is superior to curent distance, keep it
                                        if (newDistance < oldDistance) {

                                            swap.animate()
                                            // if swap is superior to the last best found solution, save it as the new best solution
                                            if (newDistance < bestDistance) {
                                                bestDistance = newDistance

                                                bestSolution = Tour.toConfiguration()
                                            }
                                        }
                                        // shall I take an inferior move? Let's flip a coin
                                        else {
                                            // Desmos graph for intuition: https://www.desmos.com/calculator/rpfpfiq7ce
                                            if (weightedCoinFlip(
                                                            exp((-(newDistance - oldDistance)) / temp)
                                                    )
                                            ) {
                                                swap.animate()
                                            } else {
                                                swap.reverse()
                                            }
                                        }
                                    }
                                }

                        sequentialTransition += timeline(play=false) {
                            keyframe(1.millis) {
                                keyvalue(Parameters.animatedTempProperty, temp / 80 )
                            }
                        }
            }

            (1..10).forEach {
                Tour.conflicts.forEach { (x, y) ->
                    x.attemptTwoSwap(y)?.animate()
                }
            }

            // apply best found model
            if (Tour.tourDistance > bestDistance) {
                Tour.applyConfiguration(bestSolution)
                Edge.all.forEach { it.animateChange() }
            }

//            println("$bestDistance<==>${Tour.tourDistance}")
            println("Simulated Annealing: ${Tour.tourDistance}")
            defaultAnimationOn = true

        }
    };

    abstract fun execute()

    companion object {
        fun prepare() {
            sequentialTransition.children.clear()
            Edge.clearAndRebuild()
            println("=============================================================")
        }
    }
}