package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFDescStatsReply;

/**
 * Encapsulates the switch information return from the description stats request
 *
 * @author Rob Vaterlaus <rob.vaterlaus@bigswitch.com>
 */
public class SwitchDescription {

    public static class Builder {

        private String manufacturerDescription;
        private String hardwareDescription;
        private String softwareDescription;
        private String serialNumber;
        private String datapathDescription;

        public Builder() {
            manufacturerDescription = "";
            hardwareDescription = "";
            softwareDescription = "";
            serialNumber = "";
            datapathDescription = "";
        }

        public Builder setManufacturerDescription(String manufacturerDescription) {
            this.manufacturerDescription = manufacturerDescription;
            return this;
        }

        public Builder setHardwareDescription(String hardwareDescription) {
            this.hardwareDescription = hardwareDescription;
            return this;
        }

        public Builder setSoftwareDescription(String softwareDescription) {
            this.softwareDescription = softwareDescription;
            return this;
        }

        public Builder setSerialNumber(String serialNumber) {
            this.serialNumber = serialNumber;
            return this;
        }

        public Builder setDatapathDescription(String datapathDescription) {
            this.datapathDescription = datapathDescription;
            return this;
        }

        public SwitchDescription build() {
            return new SwitchDescription(manufacturerDescription,
                    hardwareDescription, softwareDescription, serialNumber,
                    datapathDescription);
        }
    }

    private final String manufacturerDescription;
    private final String hardwareDescription;
    private final String softwareDescription;
    private final String serialNumber;
    private final String datapathDescription;

    public static Builder builder() {
        return new Builder();
    }

    // FIXME: Should make this private
    public SwitchDescription() {
        this("", "", "", "", "");
    }

    // FIXME: Should make this private
    public SwitchDescription(String manufacturerDescription,
            String hardwareDescription, String softwareDescription,
            String serialNumber, String datapathDescription) {
        this.manufacturerDescription = manufacturerDescription;
        this.hardwareDescription = hardwareDescription;
        this.softwareDescription = softwareDescription;
        this.serialNumber = serialNumber;
        this.datapathDescription = datapathDescription;
    }

    public SwitchDescription(OFDescStatsReply descStatsReply) {
        this(descStatsReply.getMfrDesc(), descStatsReply.getHwDesc(),
                descStatsReply.getSwDesc(), descStatsReply.getSerialNum(),
                descStatsReply.getDpDesc());
    }

    public String getManufacturerDescription() {
        return manufacturerDescription;
    }

    public String getHardwareDescription() {
        return hardwareDescription;
    }

    public String getSoftwareDescription() {
        return softwareDescription;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getDatapathDescription() {
        return datapathDescription;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((datapathDescription == null) ? 0 : datapathDescription
                        .hashCode());
        result = prime
                * result
                + ((hardwareDescription == null) ? 0 : hardwareDescription
                        .hashCode());
        result = prime
                * result
                + ((manufacturerDescription == null) ? 0
                        : manufacturerDescription.hashCode());
        result = prime * result
                + ((serialNumber == null) ? 0 : serialNumber.hashCode());
        result = prime
                * result
                + ((softwareDescription == null) ? 0 : softwareDescription
                        .hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SwitchDescription other = (SwitchDescription) obj;
        if (datapathDescription == null) {
            if (other.datapathDescription != null)
                return false;
        } else if (!datapathDescription.equals(other.datapathDescription))
            return false;
        if (hardwareDescription == null) {
            if (other.hardwareDescription != null)
                return false;
        } else if (!hardwareDescription.equals(other.hardwareDescription))
            return false;
        if (manufacturerDescription == null) {
            if (other.manufacturerDescription != null)
                return false;
        } else if (!manufacturerDescription
                .equals(other.manufacturerDescription))
            return false;
        if (serialNumber == null) {
            if (other.serialNumber != null)
                return false;
        } else if (!serialNumber.equals(other.serialNumber))
            return false;
        if (softwareDescription == null) {
            if (other.softwareDescription != null)
                return false;
        } else if (!softwareDescription.equals(other.softwareDescription))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "SwitchDescription [manufacturerDescription=" + manufacturerDescription
                + ", hardwareDescription=" + hardwareDescription + ", softwareDescription="
                + softwareDescription + ", serialNumber=" + serialNumber
                + ", datapathDescription=" + datapathDescription + "]";
    }
}
