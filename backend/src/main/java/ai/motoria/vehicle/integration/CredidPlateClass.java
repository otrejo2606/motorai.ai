package ai.motoria.vehicle.integration;

/**
 * Plate class and number sent to Credid. Rule of the MNK WSO2 gateway (seqBackGroundCredid, design D6 of
 * add-consulta-credid-personas): a normalized plate starting with CL is class CL, with C is class C, D followed by a
 * digit is class D (the number is the rest of the plate in those three cases); anything else is class A with the whole
 * plate. The MOPT patterns per plate type will replace this rule.
 *
 * @param plateClass CL, C, D or A
 * @param number     plate number sent in the "placa" parameter
 */
public record CredidPlateClass(String plateClass, String number) {

    public static final String CLASS_A = "A";
    public static final String CLASS_C = "C";
    public static final String CLASS_CL = "CL";
    public static final String CLASS_D = "D";

    /**
     * @param normalizedPlate upper case letters and digits only (PlateNormalizer)
     */
    public static CredidPlateClass of(String normalizedPlate) {
        String plate = normalizedPlate == null ? "" : normalizedPlate;
        if (plate.startsWith(CLASS_CL) && plate.length() > 2) {
            return new CredidPlateClass(CLASS_CL, plate.substring(2));
        }
        if (plate.startsWith(CLASS_C) && plate.length() > 1) {
            return new CredidPlateClass(CLASS_C, plate.substring(1));
        }
        if (plate.length() > 1 && plate.charAt(0) == 'D' && isDigit(plate.charAt(1))) {
            return new CredidPlateClass(CLASS_D, plate.substring(1));
        }
        return new CredidPlateClass(CLASS_A, plate);
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
