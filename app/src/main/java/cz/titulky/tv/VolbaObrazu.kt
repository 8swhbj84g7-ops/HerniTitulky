package cz.titulky.tv

/** Automatická volba TV dekodéru se potvrzuje snímkem, nikoli vypršením časovače. */
internal class VolbaObrazu(glUlozene: Boolean, glOverene: Boolean, yuvOverene: Boolean) {
    @Volatile var yuvOvereno = yuvOverene
        private set
    @Volatile var glOvereno = glOverene
        private set
    @Volatile var gl = glUlozene && !yuvOverene
        private set
    val ulozitGl get() = gl && glOvereno && !yuvOvereno

    @Synchronized fun prepnoutNaGl(): Boolean {
        if (gl || yuvOvereno) return false
        gl = true
        return true
    }

    @Synchronized fun snimek(presGl: Boolean): Boolean {
        if (presGl != gl) return false
        if (presGl) {
            if (glOvereno) return false
            glOvereno = true
        } else {
            if (yuvOvereno) return false
            yuvOvereno = true
        }
        return true
    }

    @Synchronized fun vratitNeovereneGl(): Boolean {
        if (!gl || glOvereno) return false
        gl = false
        return true
    }
}
