import React from 'react';

interface Props {
  buttons: React.ReactNode[];
}

const PageExtraButtons = (props: Props) => {
  return (
    <div
      style={{
        gap: 10,
        display: 'flex',
        justifyContent: 'center',
      }}
    >
      {props.buttons.map((item, index) => {
        return <React.Fragment key={index}>{item}</React.Fragment>;
      })}
    </div>
  );
};

export default PageExtraButtons;