/**
 * Shared-kernel ports for the few synchronous interactions between modules (design D2, "Puertos entre modulos").
 * Each interface is implemented by the module that owns the data; callers only see these interfaces and records,
 * so modules never import each other.
 */
package ai.motoria.common.port;
