import { ref } from 'vue'

// Invalidaciones sin datos ni credenciales. Cada componente conserva su carga
// cancelable con useRecurso; el numero avisa que su lectura quedo obsoleta.
export const revisionAvisos = ref(0)
export const revisionEmpresa = ref(0)
export const avisosActualizados = () => { revisionAvisos.value++ }
export const empresaActualizada = () => { revisionEmpresa.value++ }
